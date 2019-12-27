package com.waz.zclient.storage.db.clients.migration

enum class ContentKey(val str: String) {
    //Shared keys
    CLIENT_ID_KEY("id"),
    CLIENT_LABEL_KEY("label"),
    CLIENT_LOCATION_LAT_KEY("lat"),
    CLIENT_LOCATION_LONG_KEY("lon"),
    CLIENT_ENC_KEY("encKey"),
    CLIENT_MAC_KEY("macKey"),
    CLIENT_VERIFICATION_KEY("verification"),
    CLIENT_MODEL_KEY("model"),

    //New table keys
    NEW_CLIENT_LOCATION_NAME_KEY("locationName"),
    NEW_CLIENT_TIME_KEY("time"),
    NEW_CLIENT_TYPE_KEY("type"),

    //Old table keys
    OLD_CLIENT_REG_KEY("regTime"),
    OLD_CLIENT_LOCATION_KEY("regLocation"),
    OLD_CLIENT_LOCATION_NAME_KEY("name"),
    OLD_CLIENT_SIGNALING_KEY("signalingKey"),
    OLD_CLIENT_TYPE_KEY("devType")
}
