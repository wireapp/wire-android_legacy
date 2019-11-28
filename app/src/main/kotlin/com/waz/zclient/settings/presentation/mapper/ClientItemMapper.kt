package com.waz.zclient.settings.presentation.mapper

import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.settings.presentation.ui.devices.model.ClientItem

fun List<Client>.toPresentationList(): List<ClientItem> = map { ClientItem(it.time, it.label, it.id) }

fun Client.toPresentationObject() = ClientItem(time = time, label = label, id = id)

