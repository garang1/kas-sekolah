package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.dao.TransactionDao
import com.example.data.model.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SyncFetchResult(
    val transactions: List<TransactionEntity>,
    val fundSources: List<String>
)

class GoogleSheetsSyncRepository(
    private val transactionDao: TransactionDao
) : BkuSyncRepository {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    companion object {
        private const val TAG = "GoogleSheetsSync"

        val APPS_SCRIPT_TEMPLATE = """
function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var contents = JSON.parse(e.postData.contents);
    var action = contents.action || (contents.rkasItems ? 'sync_rkas' : 'sync_all');

    purgeMixedSheets(ss);

    if (contents.fundSources && Array.isArray(contents.fundSources) && contents.fundSources.length > 0) {
      saveCustomFundSources(ss, contents.fundSources);
    }

    if (action === 'sync_fund_sources') {
      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        message: "Daftar Pos Sumber Dana berhasil diperbarui."
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 1. SINKRONISASI RKAS / PAGU
    if (action === 'sync_rkas' || contents.rkasItems) {
      var rkasItems = contents.rkasItems || [];
      var rkasBySource = {};
      
      rkasItems.forEach(function(item) {
        var src = (item.fundSource && item.fundSource.trim()) || 'BOS Reguler';
        if (!rkasBySource[src]) rkasBySource[src] = [];
        rkasBySource[src].push(item);
      });

      var headerTitles = ["No", "ID", "Uraian Belanja", "Volume", "Satuan", "Tarif Satuan (Rp)", "Jumlah Pagu (Rp)", "Sumber Dana"];

      for (var src in rkasBySource) {
        var cleanSheetName = 'RKAS_' + src.replace(/[\/\\\?\*\[\]]/g, '_').trim();
        var rSheet = ss.getSheetByName(cleanSheetName) || ss.insertSheet(cleanSheetName);
        rSheet.clear();
        
        rSheet.appendRow(headerTitles);
        rSheet.getRange(1, 1, 1, headerTitles.length).setFontWeight("bold").setBackground("#059669").setFontColor("#FFFFFF");

        var rows = [];
        var totalPagu = 0;

        rkasBySource[src].forEach(function(it, idx) {
          var tarif = parseNumberVal(it.tarifHarga);
          var jlh = parseNumberVal(it.jumlah);
          if (jlh === 0 && tarif > 0) {
            var volNum = parseNumberVal(it.volume) || 1;
            jlh = tarif * volNum;
          }
          totalPagu += jlh;

          rows.push([
            idx + 1,
            it.id || (idx + 1),
            it.uraian || '',
            it.volume || '1',
            it.satuan || 'buah',
            tarif,
            jlh,
            src
          ]);
        });

        if (rows.length > 0) {
          rSheet.getRange(2, 1, rows.length, headerTitles.length).setValues(rows);
          rSheet.getRange(2, 6, rows.length, 2).setNumberFormat("#,##0");

          var totalRow = ["", "", "TOTAL PAGU ANGGARAN", "", "", "", totalPagu, src];
          rSheet.appendRow(totalRow);
          var lastRowIdx = rows.length + 2;
          rSheet.getRange(lastRowIdx, 1, 1, headerTitles.length).setFontWeight("bold").setBackground("#ECFDF5");
          rSheet.getRange(lastRowIdx, 7, 1, 1).setNumberFormat("#,##0");
        }
      }

      cleanupDefaultSheet(ss);
      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        count: rkasItems.length,
        message: "Sinkronisasi RKAS berhasil."
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 2. SINKRONISASI BKU / TRANSAKSI
    var transactions = contents.transactions || [];

    if (transactions.length === 0) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        count: 0,
        message: "Data transaksi lokal kosong."
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var txBySource = {};

    transactions.forEach(function(tx) {
      var src = (tx.fundSource && tx.fundSource.trim()) || 'BOS Reguler';
      if (!txBySource[src]) txBySource[src] = [];
      txBySource[src].push(tx);
    });

    var bkuHeaders = [
      "No",
      "No ID Transaksi",
      "Tanggal",
      "Sumber Dana",
      "Uraian Transaksi",
      "Volume",
      "Satuan",
      "Harga Satuan (Rp)",
      "Pemasukan (Rp)",
      "Pengeluaran (Rp)",
      "Saldo Kas (Rp)",
      "Status Verifikasi",
      "Pencatat",
      "Catatan"
    ];

    var updatedSheetNames = [];

    for (var fundSource in txBySource) {
      var sheetName = 'BKU_' + fundSource.replace(/[\/\\\?\*\[\]]/g, '_').trim();
      var bkuSheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);
      bkuSheet.clear();

      bkuSheet.appendRow(bkuHeaders);
      bkuSheet.getRange(1, 1, 1, bkuHeaders.length).setFontWeight("bold").setBackground("#1E40AF").setFontColor("#FFFFFF");

      var listTx = txBySource[fundSource].slice().sort(function(a, b) {
        var dateA = typeof a.date === 'number' ? a.date : new Date(a.date).getTime();
        var dateB = typeof b.date === 'number' ? b.date : new Date(b.date).getTime();
        return (dateA || 0) - (dateB || 0);
      });

      var sRows = [];
      var runningBalance = 0;
      var totalIncome = 0;
      var totalExpense = 0;

      listTx.forEach(function(tx, idx) {
        var dateStr = formatDateVal(tx.date);
        var rawAmount = parseNumberVal(tx.amount);
        var isIncome = (tx.type === 'PEMASUKAN' || tx.type === 'INCOME');
        var inc = isIncome ? rawAmount : 0;
        var exp = !isIncome ? rawAmount : 0;

        totalIncome += inc;
        totalExpense += exp;
        runningBalance = runningBalance + inc - exp;

        var vol = parseNumberVal(tx.volume) || 1;
        var sat = String(tx.unitName || tx.satuan || (isIncome ? 'transaksi' : 'buah')).trim();
        var uPrice = parseNumberVal(tx.unitPrice);
        if (uPrice <= 0) {
          uPrice = vol > 0 ? (rawAmount / vol) : rawAmount;
        }

        var roleText = 'Bendahara';
        if (tx.recordedByRole) {
          var rLower = String(tx.recordedByRole).toLowerCase();
          if (rLower.indexOf('kepala') !== -1 || rLower.indexOf('kepsek') !== -1) {
            roleText = 'Kepala Sekolah';
          } else {
            roleText = 'Bendahara';
          }
        }

        var cleanNotes = tx.notes ? String(tx.notes).replace(/^Vol:\s*[^•]+•\s*/i, '').trim() : '';

        sRows.push([
          idx + 1,
          tx.id || (idx + 1),
          dateStr,
          fundSource,
          tx.title || tx.description || '',
          vol,
          sat,
          uPrice,
          inc,
          exp,
          runningBalance,
          tx.approvalStatus || 'VERIFIED',
          roleText,
          cleanNotes
        ]);
      });

      if (sRows.length > 0) {
        bkuSheet.getRange(2, 1, sRows.length, bkuHeaders.length).setValues(sRows);
        bkuSheet.getRange(2, 8, sRows.length, 4).setNumberFormat("#,##0");

        var totalRow = [
          "",
          "",
          "",
          fundSource,
          "TOTAL KESELURUHAN " + fundSource.toUpperCase(),
          "",
          "",
          "",
          totalIncome,
          totalExpense,
          runningBalance,
          "",
          "",
          ""
        ];
        bkuSheet.appendRow(totalRow);
        var lastRowIdx = sRows.length + 2;
        bkuSheet.getRange(lastRowIdx, 1, 1, bkuHeaders.length).setFontWeight("bold").setBackground("#EFF6FF");
        bkuSheet.getRange(lastRowIdx, 9, 1, 3).setNumberFormat("#,##0");
      }

      updatedSheetNames.push(sheetName);
    }

    if (Object.keys(txBySource).length === 0) {
      var defaultSheet = ss.getSheetByName('BKU_BOS Reguler') || ss.insertSheet('BKU_BOS Reguler');
      defaultSheet.clear();
      defaultSheet.appendRow(bkuHeaders);
      defaultSheet.getRange(1, 1, 1, bkuHeaders.length).setFontWeight("bold").setBackground("#1E40AF").setFontColor("#FFFFFF");
      updatedSheetNames.push('BKU_BOS Reguler');
    }

    cleanupDefaultSheet(ss);

    return ContentService.createTextOutput(JSON.stringify({
      status: "SUCCESS",
      count: transactions.length,
      message: "Berhasil sinkron " + transactions.length + " transaksi ke: " + updatedSheetNames.join(', ')
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "ERROR", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function saveCustomFundSources(ss, sources) {
  try {
    var sheet = ss.getSheetByName('_CONFIG_POS_DANA') || ss.insertSheet('_CONFIG_POS_DANA');
    sheet.clear();
    sheet.appendRow(["Pos Sumber Dana"]);
    
    var bkuHeaders = ["No", "No ID Transaksi", "Tanggal", "Sumber Dana", "Uraian Transaksi", "Volume", "Satuan", "Harga Satuan (Rp)", "Pemasukan (Rp)", "Pengeluaran (Rp)", "Saldo Kas (Rp)", "Status Verifikasi", "Pencatat", "Catatan"];
    var rkasHeaders = ["No", "ID", "Uraian Belanja", "Volume", "Satuan", "Tarif Satuan (Rp)", "Jumlah Pagu (Rp)", "Sumber Dana"];

    sources.forEach(function(s) {
      if (s && String(s).trim()) {
        var src = String(s).trim();
        sheet.appendRow([src]);
        
        var bkuName = 'BKU_' + src.replace(/[\/\\?\*\[\]]/g, '_').trim();
        if (!ss.getSheetByName(bkuName)) {
            var bkuSheet = ss.insertSheet(bkuName);
            bkuSheet.appendRow(bkuHeaders);
            bkuSheet.getRange(1, 1, 1, bkuHeaders.length).setFontWeight("bold").setBackground("#1E40AF").setFontColor("#FFFFFF");
        }
        
        var rkasName = 'RKAS_' + src.replace(/[\/\\?\*\[\]]/g, '_').trim();
        if (!ss.getSheetByName(rkasName)) {
            var rkasSheet = ss.insertSheet(rkasName);
            rkasSheet.appendRow(rkasHeaders);
            rkasSheet.getRange(1, 1, 1, rkasHeaders.length).setFontWeight("bold").setBackground("#059669").setFontColor("#FFFFFF");
        }
      }
    });
    sheet.hideSheet();
    if (typeof cleanupDefaultSheet === 'function') cleanupDefaultSheet(ss);
  } catch (e) {}
}

function getAllFundSources(ss) {
  var sources = [];
  try {
    var cfgSheet = ss.getSheetByName('_CONFIG_POS_DANA');
    if (cfgSheet) {
      var cfgData = cfgSheet.getDataRange().getValues();
      for (var k = 1; k < cfgData.length; k++) {
        var v = String(cfgData[k][0] || '').trim();
        if (v && sources.indexOf(v) === -1) sources.push(v);
      }
    }
  } catch (e) {}

  ss.getSheets().forEach(function(s) {
    var sName = s.getName();
    if (sName.indexOf('BKU_') === 0 && sName !== 'BKU_Semua' && sName !== 'SEMUA BKU') {
      var n = sName.replace('BKU_', '').trim();
      if (n && sources.indexOf(n) === -1) sources.push(n);
    }
    if (sName.indexOf('RKAS_') === 0 && sName !== 'RKAS_Semua') {
      var n = sName.replace('RKAS_', '').trim();
      if (n && sources.indexOf(n) === -1) sources.push(n);
    }
  });

  if (sources.length === 0) {
    sources = ["BOS Reguler", "BOS Kinerja", "BOS Afirmasi", "SiLPA BOS", "Infaq / Komite", "BOS Daerah"];
  }
  return sources;
}

function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var action = (e && e.parameter && e.parameter.action) || 'get_all';
    var allFundSources = getAllFundSources(ss);

    function extractAllRkasItems() {
      var allSheets = ss.getSheets();
      var rkasCandidateSheets = [];

      allSheets.forEach(function(s) {
        var n = s.getName().trim();
        var nLower = n.toLowerCase();
        if (nLower.indexOf('bku_') === 0) return;
        if (nLower === '_config_pos_dana') return;
        
        if (nLower.indexOf('rkas') !== -1 || nLower.indexOf('kertas') !== -1 || 
            nLower.indexOf('kerja') !== -1 || nLower.indexOf('anggaran') !== -1 || 
            nLower.indexOf('pagu') !== -1 || nLower.indexOf('rapbs') !== -1 || 
            nLower.indexOf('belanja') !== -1 || nLower.indexOf('kegiatan') !== -1) {
          rkasCandidateSheets.push(s);
        }
      });

      if (rkasCandidateSheets.length === 0) {
        allSheets.forEach(function(s) {
          var nLower = s.getName().trim().toLowerCase();
          if (nLower.indexOf('bku_') !== 0 && nLower !== '_config_pos_dana') {
            rkasCandidateSheets.push(s);
          }
        });
      }

      var items = [];
      rkasCandidateSheets.forEach(function(sheet) {
        var data = sheet.getDataRange().getValues();
        if (!data || data.length === 0) return;

        var bestHeaderRowIdx = 0;
        var maxScore = -1;
        var detectedCols = {
          uraian: 2, rek: 0, prog: 1, vol: 3, sat: 4, tarif: 5, jml: 6, fund: 7, id: -1, noUrut: -1
        };

        var scanLimit = Math.min(data.length, 25);
        for (var r = 0; r < scanLimit; r++) {
          var rowCells = data[r];
          if (!rowCells || rowCells.length === 0) continue;
          var score = 0;
          var tempCols = {
            uraian: -1, rek: -1, prog: -1, vol: -1, sat: -1, tarif: -1, jml: -1, fund: -1, id: -1, noUrut: -1
          };

          for (var c = 0; c < rowCells.length; c++) {
            var h = String(rowCells[c] || '').trim().toLowerCase();
            if (!h) continue;

            if (h === 'satuan' || h === 'sat' || h === 'unit' || h.indexOf('satuan') !== -1 || h.indexOf('unit') !== -1) {
              tempCols.sat = c;
              score += 4;
            } else if (h.indexOf('uraian') !== -1 || h.indexOf('kegiatan') !== -1 || h.indexOf('nama barang') !== -1 || h.indexOf('rincian') !== -1 || h.indexOf('deskripsi') !== -1 || h.indexOf('barang') !== -1) {
              tempCols.uraian = c;
              score += 3;
            } else if (h.indexOf('rekening') !== -1 || h.indexOf('kode rek') !== -1 || h.indexOf('kode akun') !== -1 || h.indexOf('akun') !== -1 || h.indexOf('mak') !== -1) {
              tempCols.rek = c;
              score += 2;
            } else if (h.indexOf('program') !== -1 || h.indexOf('kode prog') !== -1 || h.indexOf('snp') !== -1 || h.indexOf('standar') !== -1 || h.indexOf('komponen') !== -1) {
              tempCols.prog = c;
              score += 2;
            } else if (h.indexOf('vol') !== -1 || h.indexOf('kuantitas') !== -1 || h.indexOf('qty') !== -1 || h.indexOf('banyak') !== -1) {
              tempCols.vol = c;
              score += 2;
            } else if (h.indexOf('tarif') !== -1 || h.indexOf('harga') !== -1 || h.indexOf('biaya') !== -1) {
              tempCols.tarif = c;
              score += 2;
            } else if (h.indexOf('jumlah') !== -1 || h.indexOf('total') !== -1 || h.indexOf('pagu') !== -1 || h.indexOf('anggaran') !== -1) {
              tempCols.jml = c;
              score += 2;
            } else if (h.indexOf('sumber') !== -1 || h.indexOf('dana') !== -1 || h.indexOf('kas') !== -1) {
              tempCols.fund = c;
              score += 2;
            } else if (h === 'id' || h === 'kode id' || h.indexOf('id item') !== -1) {
              tempCols.id = c;
              score += 1;
            } else if (h.indexOf('no') !== -1 || h.indexOf('urut') !== -1) {
              tempCols.noUrut = c;
              score += 1;
            }
          }

          if (score > maxScore) {
            maxScore = score;
            bestHeaderRowIdx = r;
            for (var k in tempCols) {
              if (tempCols[k] !== -1) detectedCols[k] = tempCols[k];
            }
          }
        }

        var startRow = (maxScore >= 2) ? bestHeaderRowIdx + 1 : 0;
        
        var rawSheetName = sheet.getName();
        var defaultSheetFund = rawSheetName
          .replace(/^RKAS[_\s-]*/i, '')
          .replace(/^Kertas[_\s-]*Kerja[_\s-]*/i, '')
          .replace(/^KERTAS[_\s-]*KERJA[_\s-]*/i, '')
          .replace(/^Anggaran[_\s-]*/i, '')
          .replace(/^RAPBS[_\s-]*/i, '')
          .trim();
        if (!defaultSheetFund || defaultSheetFund === 'Semua' || defaultSheetFund === 'SEMUA') {
          defaultSheetFund = 'BOS Reguler';
        }

        for (var i = startRow; i < data.length; i++) {
          var row = data[i];
          if (!row || row.length === 0) continue;

          var uraianVal = detectedCols.uraian !== -1 && row[detectedCols.uraian] !== undefined 
            ? String(row[detectedCols.uraian]).trim() : '';
          
          if (!uraianVal) {
            for (var colIdx = 0; colIdx < row.length; colIdx++) {
              if (colIdx !== detectedCols.noUrut && colIdx !== detectedCols.vol && colIdx !== detectedCols.tarif && colIdx !== detectedCols.jml) {
                var cellTxt = String(row[colIdx] || '').trim();
                if (cellTxt.length > 2 && isNaN(cellTxt)) {
                  uraianVal = cellTxt;
                  break;
                }
              }
            }
          }

          var idVal = detectedCols.id !== -1 && row[detectedCols.id] !== undefined ? row[detectedCols.id] : '';

          if (!uraianVal && !idVal) continue;
          var uUpper = uraianVal.toUpperCase();
          if (uUpper.indexOf('TOTAL') === 0 || uUpper.indexOf('JUMLAH TOTAL') === 0 || 
              uUpper.indexOf('SUB TOTAL') === 0 || uUpper.indexOf('SUBTOTAL') === 0 || 
              uUpper.indexOf('REKAPITULASI') === 0 || uUpper.indexOf('PAGU TOTAL') === 0) {
            continue;
          }

          var tHarga = detectedCols.tarif !== -1 ? parseNumberVal(row[detectedCols.tarif]) : 0;
          var jlhPagu = detectedCols.jml !== -1 ? parseNumberVal(row[detectedCols.jml]) : 0;
          var volStr = (detectedCols.vol !== -1 && row[detectedCols.vol] !== undefined && String(row[detectedCols.vol]).trim() !== '') 
            ? String(row[detectedCols.vol]).trim() : '1';
          var vNum = parseNumberVal(volStr) || 1;

          if (jlhPagu === 0 && tHarga > 0) {
            jlhPagu = tHarga * vNum;
          } else if (tHarga === 0 && jlhPagu > 0) {
            tHarga = jlhPagu / vNum;
          }

          var srcName = (detectedCols.fund !== -1 && row[detectedCols.fund] && String(row[detectedCols.fund]).trim() !== '') 
            ? String(row[detectedCols.fund]).trim() : defaultSheetFund;
          
          if (srcName && allFundSources.indexOf(srcName) === -1) {
            allFundSources.push(srcName);
          }

          var rowId = parseInt(idVal);
          if (isNaN(rowId) || rowId <= 0) {
            rowId = (i * 100) + Math.floor(Math.random() * 90 + 10);
          }

          var noUrutVal = detectedCols.noUrut !== -1 && row[detectedCols.noUrut] !== undefined 
            ? String(row[detectedCols.noUrut]).trim() : String(items.length + 1);

          var rekVal = detectedCols.rek !== -1 && row[detectedCols.rek] !== undefined 
            ? String(row[detectedCols.rek]).trim() : '';

          var progVal = detectedCols.prog !== -1 && row[detectedCols.prog] !== undefined 
            ? String(row[detectedCols.prog]).trim() : '';

          var satVal = 'buah';
          if (detectedCols.sat !== -1 && row[detectedCols.sat] !== undefined) {
            var rawSat = String(row[detectedCols.sat]).trim();
            // Pastikan satuan bukan angka nominal atau sama dengan kolom tarif/volume
            if (rawSat !== '' && isNaN(rawSat) && rawSat !== volStr) {
              satVal = rawSat;
            }
          }

          items.push({
            id: rowId,
            noUrut: noUrutVal || String(items.length + 1),
            kodeRekening: rekVal,
            kodeProgram: progVal,
            uraian: uraianVal || ('Item Belanja ' + (items.length + 1)),
            volume: volStr,
            satuan: satVal,
            tarifHarga: tHarga,
            jumlah: jlhPagu,
            fundSource: srcName,
            isHeader: false,
            headerLevel: 0
          });
        }
      });

      return items;
    }

    if (action === 'get_rkas') {
      var rkasItems = extractAllRkasItems();
      return ContentService.createTextOutput(JSON.stringify({
        status: "SUCCESS",
        items: rkasItems,
        rkasItems: rkasItems,
        fundSources: allFundSources
      })).setMimeType(ContentService.MimeType.JSON);
    }

    var bkuSheets = ss.getSheets().filter(function(s) {
      var n = s.getName();
      return n.indexOf('BKU_') === 0 && n !== 'BKU_Semua' && n !== 'SEMUA BKU';
    });

    var txList = [];
    bkuSheets.forEach(function(sheet) {
      var data = sheet.getDataRange().getValues();
      if (data.length <= 1) return;

      var headerRow = data[0] || [];
      var colMap = {
        id: 1, date: 2, fund: 3, title: 4, vol: -1, sat: -1, price: -1, inc: -1, exp: -1, balance: -1, status: -1, role: -1, notes: -1
      };

      for (var c = 0; c < headerRow.length; c++) {
        var h = String(headerRow[c] || '').trim().toLowerCase();
        if (h.indexOf('id') !== -1) colMap.id = c;
        else if (h.indexOf('tanggal') !== -1 || h.indexOf('tgl') !== -1) colMap.date = c;
        else if (h.indexOf('sumber') !== -1 || h.indexOf('dana') !== -1) colMap.fund = c;
        else if (h.indexOf('uraian') !== -1 || h.indexOf('transaksi') !== -1 || h.indexOf('keterangan') !== -1) colMap.title = c;
        else if (h.indexOf('vol') !== -1 || h.indexOf('banyak') !== -1 || h.indexOf('qty') !== -1) colMap.vol = c;
        else if (h.indexOf('satuan') !== -1 || h.indexOf('sat') !== -1 || h.indexOf('unit') !== -1) colMap.sat = c;
        else if (h.indexOf('harga') !== -1 || h.indexOf('tarif') !== -1) colMap.price = c;
        else if (h.indexOf('masuk') !== -1 || h.indexOf('terima') !== -1 || h.indexOf('penerimaan') !== -1) colMap.inc = c;
        else if (h.indexOf('keluar') !== -1 || h.indexOf('pengeluaran') !== -1 || h.indexOf('belanja') !== -1) colMap.exp = c;
        else if (h.indexOf('status') !== -1 || h.indexOf('verifikasi') !== -1) colMap.status = c;
        else if (h.indexOf('catatan') !== -1 || h.indexOf('bukti') !== -1 || h.indexOf('ket') !== -1) colMap.notes = c;
        else if (h.indexOf('catat') !== -1 || h.indexOf('pencatat') !== -1 || h.indexOf('role') !== -1) colMap.role = c;
      }

      if (colMap.inc === -1 && colMap.exp === -1) {
        if (colMap.vol !== -1) {
          colMap.inc = 8;
          colMap.exp = 9;
          colMap.status = 11;
          colMap.role = 12;
          colMap.notes = 13;
        } else {
          colMap.inc = 5;
          colMap.exp = 6;
          colMap.status = 8;
          colMap.role = 9;
          colMap.notes = 10;
        }
      }

      for (var i = 1; i < data.length; i++) {
        var row = data[i];
        var rowTitle = colMap.title !== -1 && row[colMap.title] ? String(row[colMap.title]).trim() : '';
        if (!row[colMap.id] && !rowTitle) continue;
        if (rowTitle.toUpperCase().indexOf('TOTAL') === 0 || String(row[colMap.fund] || '').toUpperCase().indexOf('TOTAL') === 0) continue;

        var pIncome = colMap.inc !== -1 ? parseNumberVal(row[colMap.inc]) : 0;
        var pExpense = colMap.exp !== -1 ? parseNumberVal(row[colMap.exp]) : 0;
        var tAmount = pIncome > 0 ? pIncome : pExpense;
        var tType = pIncome > 0 ? 'PEMASUKAN' : 'PENGELUARAN';
        var fSrc = (colMap.fund !== -1 && row[colMap.fund]) ? String(row[colMap.fund]).trim() : (sheet.getName().replace('BKU_', '') || 'BOS Reguler');
        
        var vVal = (colMap.vol !== -1 && row[colMap.vol] !== undefined && String(row[colMap.vol]).trim() !== '') ? parseNumberVal(row[colMap.vol]) : 1;
        var sVal = (colMap.sat !== -1 && row[colMap.sat] !== undefined && String(row[colMap.sat]).trim() !== '') ? String(row[colMap.sat]).trim() : (tType === 'PEMASUKAN' ? 'transaksi' : 'buah');
        var pVal = (colMap.price !== -1 && row[colMap.price] !== undefined) ? parseNumberVal(row[colMap.price]) : 0;
        if (pVal <= 0 && vVal > 0) {
          pVal = tAmount / vVal;
        }

        if (fSrc && allFundSources.indexOf(fSrc) === -1) allFundSources.push(fSrc);

        var dVal = colMap.date !== -1 ? row[colMap.date] : null;
        var dTime = Date.now();
        if (dVal instanceof Date) {
          dTime = dVal.getTime();
        } else if (typeof dVal === 'string' && dVal.length >= 8) {
          var parsed = Date.parse(dVal);
          if (!isNaN(parsed)) dTime = parsed;
        }

        txList.push({
          id: parseInt(row[colMap.id]) || i,
          title: rowTitle,
          amount: tAmount,
          type: tType,
          fundSource: fSrc,
          category: 'Umum',
          volume: vVal || 1,
          unitName: sVal || 'buah',
          unitPrice: pVal || tAmount,
          date: dTime,
          notes: (colMap.notes !== -1 && row[colMap.notes]) ? String(row[colMap.notes]) : '',
          approvalStatus: (colMap.status !== -1 && row[colMap.status]) ? String(row[colMap.status]) : 'VERIFIED',
          recordedByRole: (colMap.role !== -1 && row[colMap.role]) ? String(row[colMap.role]) : 'Bendahara'
        });
      }
    });

    var allRkas = extractAllRkasItems();

    return ContentService.createTextOutput(JSON.stringify({
      status: "SUCCESS",
      transactions: txList,
      rkasItems: allRkas,
      items: allRkas,
      fundSources: allFundSources
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "ERROR", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function parseNumberVal(val) {
  if (typeof val === 'number') return isNaN(val) ? 0 : val;
  if (!val) return 0;
  var s = String(val).trim();
  s = s.replace(/Rp\.?\s?/gi, '');
  if (s.indexOf('.') !== -1 && s.indexOf(',') !== -1) {
    s = s.replace(/\./g, '').replace(',', '.');
  } else if (s.indexOf('.') !== -1 && s.indexOf(',') === -1) {
    var parts = s.split('.');
    if (parts.length > 2 || (parts.length === 2 && parts[1].length === 3)) {
      s = s.replace(/\./g, '');
    }
  } else if (s.indexOf(',') !== -1 && s.indexOf('.') === -1) {
    var parts2 = s.split(',');
    if (parts2.length > 2 || (parts2.length === 2 && parts2[1].length === 3)) {
      s = s.replace(/,/g, '');
    } else {
      s = s.replace(',', '.');
    }
  }
  s = s.replace(/[^0-9.-]+/g, "");
  var num = parseFloat(s);
  return isNaN(num) ? 0 : num;
}

function formatDateVal(d) {
  if (!d) return Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy-MM-dd HH:mm");
  try {
    var dt = (typeof d === 'number') ? new Date(d) : ((d instanceof Date) ? d : new Date(d));
    if (isNaN(dt.getTime())) dt = new Date();
    return Utilities.formatDate(dt, "Asia/Jakarta", "yyyy-MM-dd HH:mm");
  } catch (e) {
    return Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy-MM-dd HH:mm");
  }
}

function purgeMixedSheets(ss) {
  var mixedNames = [
    'BKU_Semua', 'SEMUA BKU', 'Kas_Semua', 'KAS_SEMUA', 'Kas_Semua_Sumber_Dana',
    'Semua_Kas', 'Semua Kas', 'Semua_BKU', 'Semua BKU'
  ];
  mixedNames.forEach(function(name) {
    var sh = ss.getSheetByName(name);
    if (sh && ss.getSheets().length > 1) {
      try { ss.deleteSheet(sh); } catch(e) {}
    }
  });
}

function cleanupDefaultSheet(ss) {
  var defaultSheets = ['Sheet1', 'Sheet 1', 'Lembar1', 'Lembar 1'];
  if (ss.getSheets().length > 1) {
    defaultSheets.forEach(function(name) {
      var sh = ss.getSheetByName(name);
      if (sh && sh.getLastRow() <= 1 && ss.getSheets().length > 1) {
        try { ss.deleteSheet(sh); } catch(e) {}
      }
    });
  }
}
""".trimIndent()
    }

    override suspend fun exportToCsv(
        context: Context,
        transactions: List<TransactionEntity>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            
            val fileName = "BKU_Sekolah_${fileDateFormat.format(Date())}.csv"
            
            // Simpan ke Downloads / Dokumen di Penyimpanan Internal HP (jika tersedia), fallback ke cacheDir
            val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                File(publicDir, fileName)
            } else {
                File(context.cacheDir, fileName)
            }

            val csvBuilder = StringBuilder()
            // Header for Google Sheets / Excel Indonesian BKU standard
            csvBuilder.append("No,ID,Tanggal,Sumber Dana,Tipe,Uraian Transaksi,Volume,Satuan,Harga Satuan (Rp),Penerimaan (Rp),Pengeluaran (Rp),Status Approval,Pencatat,Catatan\n")

            transactions.forEachIndexed { index, tx ->
                val dateStr = dateFormat.format(Date(tx.date))
                val income = if (tx.type == "PEMASUKAN") tx.amount.toLong() else 0
                val expense = if (tx.type == "PENGELUARAN") tx.amount.toLong() else 0
                val escapedTitle = "\"${tx.title.replace("\"", "\"\"")}\""
                val cleanNotes = tx.notes.replace(Regex("^Vol:\\s*[^•]+•\\s*", RegexOption.IGNORE_CASE), "").trim()
                val escapedNotes = "\"${cleanNotes.replace("\"", "\"\"")}\""
                val vol = if (tx.volume > 0) tx.volume else 1.0
                val sat = tx.unitName.ifBlank { if (tx.type == "PEMASUKAN") "transaksi" else "buah" }
                val uPrice = if (tx.unitPrice > 0) tx.unitPrice.toLong() else (if (vol > 0) (tx.amount / vol).toLong() else tx.amount.toLong())

                csvBuilder.append("${index + 1},${tx.id},\"$dateStr\",\"${tx.fundSource}\",\"${tx.type}\",$escapedTitle,$vol,\"$sat\",$uPrice,$income,$expense,\"${tx.approvalStatus}\",\"${tx.recordedByRole}\",$escapedNotes\n")
            }

            file.writeText(csvBuilder.toString(), Charsets.UTF_8)

            // Beritahukan Android Media Scanner agar file langsung muncul di File Manager HP (Storage Internal)
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("text/csv")
                ) { _, _ -> }
            } catch (ignored: Exception) {}

            // Share / Buka file via FileProvider Intent
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Laporan BKU Sekolah (CSV)")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Simpan / Buka File CSV BKU (Tersimpan di Download)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            val successMsg = "File CSV tersimpan di Download HP: $fileName"
            _syncState.value = SyncState.Success(successMsg)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal ekspor CSV", e)
            val err = "Gagal ekspor CSV: ${e.localizedMessage}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    suspend fun syncFundSources(
        webAppUrl: String,
        fundSources: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val trimmedUrl = webAppUrl.trim()
        if (trimmedUrl.isBlank() || fundSources.isEmpty()) {
            return@withContext Result.success(true)
        }
        try {
            val payload = org.json.JSONObject().apply {
                put("action", "sync_fund_sources")
                val arr = org.json.JSONArray()
                fundSources.forEach { arr.put(it) }
                put("fundSources", arr)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(trimmedUrl).post(requestBody).build()
            val response = okHttpClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sync Pos Sumber Dana ke Google Sheets", e)
            Result.failure(e)
        }
    }

    override suspend fun syncToGoogleSheets(
        webAppUrl: String,
        transactions: List<TransactionEntity>
    ): Result<Int> = syncToGoogleSheets(webAppUrl, transactions, emptyList())

    suspend fun syncToGoogleSheets(
        webAppUrl: String,
        transactions: List<TransactionEntity>,
        fundSources: List<String> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        val trimmedUrl = webAppUrl.trim()
        if (trimmedUrl.isBlank()) {
            val err = "URL Google Apps Script Web App belum diisi."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (trimmedUrl.contains("docs.google.com/spreadsheets")) {
            val err = "URL yang dimasukkan adalah link Spreadsheet Google Sheets (docs.google.com). Gunakan URL Aplikasi Web dari Apps Script (https://script.google.com/macros/s/.../exec)."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _syncState.value = SyncState.Syncing
        try {
            val payload = org.json.JSONObject().apply {
                put("action", "sync_all")
                val arr = org.json.JSONArray()
                for (tx in transactions) {
                    val obj = org.json.JSONObject().apply {
                        put("id", tx.id)
                        put("title", tx.title)
                        put("amount", tx.amount)
                        put("type", tx.type)
                        put("fundSource", tx.fundSource)
                        put("category", tx.category)
                        put("volume", tx.volume)
                        put("unitName", tx.unitName)
                        put("unitPrice", tx.unitPrice)
                        put("date", tx.date)
                        put("notes", tx.notes)
                        put("receiptUri", tx.receiptUri ?: "")
                        put("approvalStatus", tx.approvalStatus)
                        put("recordedByRole", tx.recordedByRole)
                        put("createdAt", tx.createdAt)
                    }
                    arr.put(obj)
                }
                put("transactions", arr)

                if (fundSources.isNotEmpty()) {
                    val fsArr = org.json.JSONArray()
                    fundSources.forEach { fsArr.put(it) }
                    put("fundSources", fsArr)
                }
            }
            val jsonPayload = payload.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(trimmedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyStr = response.body?.string() ?: ""

            if (response.isSuccessful || response.code in 200..302) {
                if (responseBodyStr.trim().startsWith("<")) {
                    val err = "Gagal: Akses Google Apps Script memerlukan login. Pastikan opsi 'Who has access (Yang memiliki akses)' diset ke 'Anyone (Siapa saja)' saat Deploy Web App."
                    _syncState.value = SyncState.Error(err)
                    Result.failure(Exception(err))
                } else {
                    val msg = "Berhasil menyinkronkan ${transactions.size} transaksi BKU ke Tab masing-masing Sumber Dana di Google Sheets!"
                    _syncState.value = SyncState.Success(msg)
                    Result.success(transactions.size)
                }
            } else {
                val err = "Gagal sync ke Google Sheets (HTTP ${response.code}): $responseBodyStr"
                _syncState.value = SyncState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sync ke Google Sheets Web App", e)
            val err = "Error koneksi Google Sheets: ${e.localizedMessage}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    suspend fun syncRkasToGoogleSheets(
        webAppUrl: String,
        rkasItems: List<com.example.data.model.RkasItem>,
        fundSources: List<String> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        val trimmedUrl = webAppUrl.trim()
        if (trimmedUrl.isBlank()) {
            val err = "URL Google Apps Script Web App belum diisi."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (trimmedUrl.contains("docs.google.com/spreadsheets")) {
            val err = "URL yang dimasukkan adalah link Spreadsheet Google Sheets (docs.google.com). Gunakan URL Aplikasi Web dari Apps Script (https://script.google.com/macros/s/.../exec)."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _syncState.value = SyncState.Syncing
        try {
            val payload = org.json.JSONObject().apply {
                put("action", "sync_rkas")
                val arr = org.json.JSONArray()
                for (it in rkasItems) {
                    val obj = org.json.JSONObject().apply {
                        put("id", it.id)
                        put("noUrut", it.noUrut)
                        put("kodeRekening", it.kodeRekening)
                        put("kodeProgram", it.kodeProgram)
                        put("uraian", it.uraian)
                        put("volume", it.volume)
                        put("satuan", it.satuan)
                        put("tarifHarga", it.tarifHarga)
                        put("jumlah", it.jumlah)
                        put("fundSource", it.fundSource)
                        put("isHeader", it.isHeader)
                        put("headerLevel", it.headerLevel)
                    }
                    arr.put(obj)
                }
                put("rkasItems", arr)

                if (fundSources.isNotEmpty()) {
                    val fsArr = org.json.JSONArray()
                    fundSources.forEach { fsArr.put(it) }
                    put("fundSources", fsArr)
                }
            }
            val jsonPayload = payload.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(trimmedUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyStr = response.body?.string() ?: ""

            if (response.isSuccessful || response.code in 200..302) {
                if (responseBodyStr.trim().startsWith("<")) {
                    val err = "Gagal: Akses Google Apps Script memerlukan login. Pastikan opsi 'Who has access (Yang memiliki akses)' diset ke 'Anyone (Siapa saja)' saat Deploy Web App."
                    _syncState.value = SyncState.Error(err)
                    Result.failure(Exception(err))
                } else {
                    val msg = "Berhasil menyinkronkan ${rkasItems.size} item RKAS ke Tab masing-masing Sumber Dana di Google Sheets!"
                    _syncState.value = SyncState.Success(msg)
                    Result.success(rkasItems.size)
                }
            } else {
                val err = "Gagal sync RKAS ke Google Sheets (HTTP ${response.code}): $responseBodyStr"
                _syncState.value = SyncState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sync RKAS ke Google Sheets Web App", e)
            val err = "Error koneksi Google Sheets: ${e.localizedMessage}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    suspend fun fetchRkasFromGoogleSheets(webAppUrl: String): Result<List<com.example.data.model.RkasItem>> = withContext(Dispatchers.IO) {
        if (webAppUrl.isBlank()) {
            val err = "URL Google Apps Script Web App belum diisi."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _syncState.value = SyncState.Syncing
        try {
            val fetchUrl = if (webAppUrl.contains("?")) "$webAppUrl&action=get_rkas" else "$webAppUrl?action=get_rkas"

            val request = Request.Builder()
                .url(fetchUrl.trim())
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                if (responseBodyStr.trim().startsWith("<")) {
                    val err = "Gagal: URL Google Sheets mengembalikan HTML. Pastikan Akses diset ke 'Anyone' (Siapa Saja) di Apps Script."
                    _syncState.value = SyncState.Error(err)
                    Result.failure(Exception(err))
                } else {
                    val trimmed = responseBodyStr.trim()
                    val fetchedList: List<com.example.data.model.RkasItem> = if (trimmed.startsWith("{")) {
                        val jsonObj = org.json.JSONObject(trimmed)
                        val itemsArr = jsonObj.optJSONArray("items")
                            ?: jsonObj.optJSONArray("rkasItems")
                            ?: jsonObj.optJSONArray("data")
                            ?: org.json.JSONArray()
                        val list = mutableListOf<com.example.data.model.RkasItem>()
                        for (i in 0 until itemsArr.length()) {
                            val it = itemsArr.getJSONObject(i)
                            
                            val rawTarif = it.opt("tarifHarga") ?: it.opt("tarif") ?: it.opt("harga")
                            val tarif = when (rawTarif) {
                                is Number -> rawTarif.toDouble()
                                is String -> rawTarif.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }

                            val rawVol = it.opt("volume")?.toString() ?: "1"
                            val volNum = rawVol.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() ?: 1.0

                            val rawJumlah = it.opt("jumlah") ?: it.opt("pagu") ?: it.opt("total")
                            var jlh = when (rawJumlah) {
                                is Number -> rawJumlah.toDouble()
                                is String -> rawJumlah.replace("[^0-9.-]".toRegex(), "").toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                            if (jlh == 0.0 && tarif > 0.0) {
                                jlh = tarif * volNum
                            } else if (tarif == 0.0 && jlh > 0.0 && volNum > 0) {
                                // back-calculate tarif if only total given
                            }

                            val fundSrc = it.optString("fundSource", "").ifBlank {
                                it.optString("sumberDana", "BOS Reguler")
                            }

                            val uraianVal = it.optString("uraian", "").ifBlank {
                                it.optString("kegiatan", "").ifBlank {
                                    it.optString("namaBarang", "Item Belanja ${i + 1}")
                                }
                            }

                            val rawSat = it.optString("satuan", "buah").trim()
                            val cleanSat = if (rawSat.isNotBlank() && !rawSat.matches(Regex("^[0-9.]+$")) && rawSat != rawVol) {
                                rawSat
                            } else {
                                "buah"
                            }

                            list.add(
                                com.example.data.model.RkasItem(
                                    id = it.optInt("id", (i + 1) * 100),
                                    noUrut = it.optString("noUrut", "${i + 1}"),
                                    kodeRekening = it.optString("kodeRekening", it.optString("rekening", "")),
                                    kodeProgram = it.optString("kodeProgram", it.optString("program", "")),
                                    uraian = uraianVal,
                                    volume = rawVol,
                                    satuan = cleanSat,
                                    tarifHarga = tarif,
                                    jumlah = jlh,
                                    fundSource = fundSrc,
                                    isHeader = it.optBoolean("isHeader", false),
                                    headerLevel = it.optInt("headerLevel", 0)
                                )
                            )
                        }
                        list
                    } else if (trimmed.startsWith("[")) {
                        val itemsArr = org.json.JSONArray(trimmed)
                        val list = mutableListOf<com.example.data.model.RkasItem>()
                        for (i in 0 until itemsArr.length()) {
                            val it = itemsArr.getJSONObject(i)
                            list.add(
                                com.example.data.model.RkasItem(
                                    id = it.optInt("id", i + 1),
                                    noUrut = it.optString("noUrut", "${i + 1}"),
                                    kodeRekening = it.optString("kodeRekening", ""),
                                    kodeProgram = it.optString("kodeProgram", ""),
                                    uraian = it.optString("uraian", "Item ${i + 1}"),
                                    volume = it.optString("volume", "1"),
                                    satuan = it.optString("satuan", "buah"),
                                    tarifHarga = it.optDouble("tarifHarga", 0.0),
                                    jumlah = it.optDouble("jumlah", 0.0),
                                    fundSource = it.optString("fundSource", "BOS Reguler")
                                )
                            )
                        }
                        list
                    } else {
                        val itemListType = Types.newParameterizedType(List::class.java, com.example.data.model.RkasItem::class.java)
                        val adapter = moshi.adapter<List<com.example.data.model.RkasItem>>(itemListType)
                        adapter.fromJson(responseBodyStr) ?: emptyList()
                    }

                    val msg = "Berhasil mengunduh ${fetchedList.size} item RKAS (revisi) dari Google Sheets!"
                    _syncState.value = SyncState.Success(msg)
                    Result.success(fetchedList)
                }
            } else {
                val err = "Gagal mengambil data RKAS dari Google Sheets (HTTP ${response.code})"
                _syncState.value = SyncState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal fetch RKAS dari Google Sheets Web App", e)
            val err = "Error koneksi Google Sheets: ${e.localizedMessage}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    suspend fun fetchDetailedFromGoogleSheets(webAppUrl: String): Result<SyncFetchResult> = withContext(Dispatchers.IO) {
        if (webAppUrl.isBlank()) {
            val err = "URL Google Apps Script Web App belum diisi."
            _syncState.value = SyncState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _syncState.value = SyncState.Syncing
        try {
            val fetchUrl = if (webAppUrl.contains("?")) "$webAppUrl&action=get_all" else "$webAppUrl?action=get_all"

            val request = Request.Builder()
                .url(fetchUrl.trim())
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                if (responseBodyStr.trim().startsWith("<")) {
                    val err = "Gagal: URL Google Sheets mengembalikan HTML. Pastikan Akses diset ke 'Anyone' (Siapa Saja) di Apps Script."
                    _syncState.value = SyncState.Error(err)
                    Result.failure(Exception(err))
                } else {
                    val trimmed = responseBodyStr.trim()
                    val fetchedList = mutableListOf<TransactionEntity>()
                    val fundSourcesList = mutableListOf<String>()

                    if (trimmed.startsWith("{")) {
                        val jsonObj = org.json.JSONObject(trimmed)
                        val txArr = jsonObj.optJSONArray("transactions") ?: org.json.JSONArray()
                        for (i in 0 until txArr.length()) {
                            val it = txArr.getJSONObject(i)
                            val vol = it.optDouble("volume", 1.0)
                            val sat = it.optString("unitName", it.optString("satuan", "buah"))
                            val price = it.optDouble("unitPrice", 0.0)
                            val amt = it.optDouble("amount", 0.0)
                            val rawStatus = it.optString("approvalStatus", "VERIFIED")
                            val cleanStatus = when {
                                rawStatus.contains("PENDING", ignoreCase = true) || rawStatus.contains("MENUNGGU", ignoreCase = true) -> "PENDING_APPROVAL"
                                rawStatus.contains("TOLAK", ignoreCase = true) -> "DITOLAK"
                                else -> "VERIFIED"
                            }
                            val rawRole = it.optString("recordedByRole", "Bendahara")
                            val cleanRole = if (rawRole.contains("kepala", ignoreCase = true) || rawRole.contains("kepsek", ignoreCase = true)) "Kepala Sekolah" else "Bendahara"

                            fetchedList.add(
                                TransactionEntity(
                                    id = it.optInt("id", 0),
                                    title = it.optString("title", ""),
                                    amount = amt,
                                    type = it.optString("type", "PENGELUARAN"),
                                    fundSource = it.optString("fundSource", "BOS Reguler"),
                                    category = it.optString("category", "Umum"),
                                    volume = vol,
                                    unitName = sat,
                                    unitPrice = if (price > 0) price else (if (vol > 0) amt / vol else amt),
                                    date = it.optLong("date", System.currentTimeMillis()),
                                    notes = it.optString("notes", ""),
                                    receiptUri = it.optString("receiptUri", null.toString()).takeIf { s -> s != "null" && s.isNotBlank() },
                                    approvalStatus = cleanStatus,
                                    recordedByRole = cleanRole,
                                    createdAt = it.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }

                        val fsArr = jsonObj.optJSONArray("fundSources")
                        if (fsArr != null) {
                            for (j in 0 until fsArr.length()) {
                                val fs = fsArr.optString(j, "").trim()
                                if (fs.isNotBlank() && !fundSourcesList.contains(fs)) {
                                    fundSourcesList.add(fs)
                                }
                            }
                        }
                    } else {
                        val txListType = Types.newParameterizedType(List::class.java, TransactionEntity::class.java)
                        val adapter = moshi.adapter<List<TransactionEntity>>(txListType)
                        val list = adapter.fromJson(responseBodyStr) ?: emptyList()
                        fetchedList.addAll(list)
                    }

                    // Sinkronkan dan gabungkan transaksi dari Google Sheets dengan aman ke database lokal
                    val localTransactions = transactionDao.getAllTransactionsSync()
                    val localMapById = localTransactions.associateBy { it.id }

                    fetchedList.forEach { tx ->
                        val src = tx.fundSource.trim()
                        if (src.isNotBlank() && !fundSourcesList.contains(src)) {
                            fundSourcesList.add(src)
                        }

                        // Cek apakah transaksi sudah ada secara lokal berdasarkan konten yang sama
                        val matchByContent = localTransactions.find { local ->
                            local.title.trim().equals(tx.title.trim(), ignoreCase = true) &&
                            local.amount == tx.amount &&
                            local.fundSource.trim().equals(tx.fundSource.trim(), ignoreCase = true) &&
                            local.type == tx.type &&
                            kotlin.math.abs(local.date - tx.date) < 86400000L
                        }

                        val matchById = localMapById[tx.id]

                        val targetTx = when {
                            matchByContent != null -> {
                                // Transaksi sudah ada, perbarui status verifikasi/approval dari Google Sheets
                                matchByContent.copy(
                                    approvalStatus = tx.approvalStatus,
                                    volume = tx.volume,
                                    unitName = tx.unitName,
                                    unitPrice = tx.unitPrice,
                                    notes = if (tx.notes.isNotBlank()) tx.notes else matchByContent.notes,
                                    recordedByRole = tx.recordedByRole
                                )
                            }
                            matchById != null -> {
                                // ID sama: jika judul atau nominal sama maka update, jika tidak berikan id = 0 agar autoincrement
                                if (matchById.title.trim().equals(tx.title.trim(), ignoreCase = true) || matchById.amount == tx.amount) {
                                    matchById.copy(
                                        approvalStatus = tx.approvalStatus,
                                        volume = tx.volume,
                                        unitName = tx.unitName,
                                        unitPrice = tx.unitPrice,
                                        notes = if (tx.notes.isNotBlank()) tx.notes else matchById.notes,
                                        recordedByRole = tx.recordedByRole
                                    )
                                } else {
                                    tx.copy(id = 0)
                                }
                            }
                            else -> {
                                // Transaksi baru dari perangkat lain (misal pengeluaran bendahara)
                                tx
                            }
                        }

                        transactionDao.insertTransaction(targetTx)
                    }

                    val msg = "Berhasil mengunduh ${fetchedList.size} transaksi & ${fundSourcesList.size} Pos Sumber Dana dari Google Sheets"
                    _syncState.value = SyncState.Success(msg)
                    Result.success(SyncFetchResult(fetchedList, fundSourcesList))
                }
            } else {
                val err = "Gagal mengambil data dari Google Sheets (HTTP ${response.code})"
                _syncState.value = SyncState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal fetch dari Google Sheets Web App", e)
            val err = "Error koneksi Google Sheets: ${e.localizedMessage}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    override suspend fun fetchFromGoogleSheets(webAppUrl: String): Result<List<TransactionEntity>> = withContext(Dispatchers.IO) {
        val result = fetchDetailedFromGoogleSheets(webAppUrl)
        if (result.isSuccess) {
            Result.success(result.getOrNull()?.transactions ?: emptyList())
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    fun getAppsScriptTemplateCode(): String {
        return APPS_SCRIPT_TEMPLATE
    }
}
