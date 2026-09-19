import re

with open('app/src/main/java/com/example/data/repository/GoogleSheetsSyncRepository.kt', 'r') as f:
    content = f.read()

old_func = """function saveCustomFundSources(ss, sources) {
  try {
    var sheet = ss.getSheetByName('_CONFIG_POS_DANA') || ss.insertSheet('_CONFIG_POS_DANA');
    sheet.clear();
    sheet.appendRow(["Pos Sumber Dana"]);
    sources.forEach(function(s) {
      if (s && String(s).trim()) {
        sheet.appendRow([String(s).trim()]);
      }
    });
    sheet.hideSheet();
  } catch (e) {}
}"""

new_func = """function saveCustomFundSources(ss, sources) {
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
        
        var bkuName = 'BKU_' + src.replace(/[\/\\\?\*\[\]]/g, '_').trim();
        if (!ss.getSheetByName(bkuName)) {
            var bkuSheet = ss.insertSheet(bkuName);
            bkuSheet.appendRow(bkuHeaders);
            bkuSheet.getRange(1, 1, 1, bkuHeaders.length).setFontWeight("bold").setBackground("#1E40AF").setFontColor("#FFFFFF");
        }
        
        var rkasName = 'RKAS_' + src.replace(/[\/\\\?\*\[\]]/g, '_').trim();
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
}"""

if old_func in content:
    content = content.replace(old_func, new_func)
    with open('app/src/main/java/com/example/data/repository/GoogleSheetsSyncRepository.kt', 'w') as f:
        f.write(content)
    print("Patched successfully.")
else:
    print("Failed to find old_func.")

