package com.waz.zclient.user.domain.model


data class User(val email: String, val phone: String, val handle: String, val locale: String,
                val managedBy: String, val accentId: String, val name: String, val id: String,
                val deleted: Boolean)
