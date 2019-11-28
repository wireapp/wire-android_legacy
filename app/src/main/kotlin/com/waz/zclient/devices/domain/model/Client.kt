package com.waz.zclient.devices.domain.model

data class Client(val cookie: String?,
                  val time: String,
                  val label: String,
                  val _class: String,
                  val type: String,
                  val id: String,
                  val model: String,
                  val location: ClientLocation)

data class ClientLocation(val long: Double,
                          val lat: Double)
