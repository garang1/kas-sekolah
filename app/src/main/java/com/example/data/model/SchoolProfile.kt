package com.example.data.model

data class SchoolProfile(
    val schoolName: String = "SD Negeri 33/III Air Tenang",
    val npsn: String = "10103214",
    val address: String = "Kec. Air Hangat, Kab. Kerinci, Prov. Jambi",
    val kepalaSekolahName: String = "H. Ahmad S.Pd., M.Pd.",
    val kepalaSekolahNip: String = "197203151998031002",
    val bendaharaName: String = "Siti Rahma S.Pd.",
    val bendaharaNip: String = "198507202010012015"
)

data class UserAccountSession(
    val userName: String = "Siti Rahma S.Pd.",
    val role: UserRole = UserRole.BENDAHARA,
    val isLoggedIn: Boolean = true,
    val bendaharaPin: String = "123456",
    val kepsekPin: String = "123456"
)
