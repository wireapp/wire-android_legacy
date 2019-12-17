package com.waz.zclient.devices.domain.model

data class Client(val cookie: String? = null,
                  val time: String,
                  val label: String,
                  val _class: String,
                  val type: String,
                  val id: String,
                  val model: String,
                  val verification: String = "Unverified",
                  val encKey: String = "",
                  val macKey: String = "",
                  val location: ClientLocation)

data class ClientLocation(val lat: Double,
                          val long: Double,
                          val name: String? = null)
