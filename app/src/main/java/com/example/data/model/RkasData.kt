package com.example.data.model

data class RkasHeader(
    val npsn: String = "10502653",
    val schoolName: String = "SD NEGERI 33/III AIR TENANG",
    val address: String = "Air Tenang, Kec. Air Hangat",
    val regency: String = "Kab. Kerinci",
    val province: String = "Prov. Jambi",
    val fiscalYear: String = "2026",
    val fundSource: String = "BOSP Reguler",
    val totalRevenue: Double = 60160000.0,
    val komiteName: String = "Perdinal Hernanda",
    val kepsekName: String = "Opper Antoni",
    val kepsekNip: String = "198007162006041015",
    val bendaharaName: String = "Suprimi Saputra",
    val bendaharaNip: String = "199305202024211017",
    val docLocationDate: String = "Kec. Air Hangat, 17 Juni 2026"
)

data class RkasItem(
    val id: Int,
    val noUrut: String,
    val kodeRekening: String,
    val kodeProgram: String,
    val uraian: String,
    val volume: String = "",
    val satuan: String = "",
    val tarifHarga: Double = 0.0,
    val jumlah: Double,
    val isHeader: Boolean = false,
    val headerLevel: Int = 0, // 1: Standar Utama, 2: Program, 3: Sub-Program
    val fundSource: String = "BOS Reguler"
)

object RkasDefaults {
    val HEADER = RkasHeader()

    val ITEMS: List<RkasItem> = emptyList()
}


