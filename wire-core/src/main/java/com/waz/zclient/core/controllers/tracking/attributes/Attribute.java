/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.core.controllers.tracking.attributes;

public enum Attribute {

    REGISTRATION_WEEK("registration__week"),
    CONNECT__HAS_CONTACT("connect__has_contact"),

    SESSION_FIRST_SESSION("firstSession"),
    SESSION_SEARCHED_FOR_PEOPLE("searchedPeople"),

    APP_LAUNCH_MECHANISM("mechanism"),

    CONNECT_INVITATION_METHOD("invitationMethod"),

    GROUP_CONVERSATION_CREATED("groupConversationCreated"),

    REGISTRATION_SHARE_CONTACTS_ALLOWED("allowed"),

    REGISTRATION_FAIL_REASON("reason"),

    SIGN_IN_AFTER_PASSWORD_RESET("afterPasswordReset"),
    SIGN_IN_ERROR_CODE("reason"),

    BLOCKING("blocking"),
    UNBLOCKING("unBlocking"),

    NEW_MEMBERS("new_members"),
    REMOVED_CONTACT("removedContact"),
    LEAVE_GROUP("leaveGroup"),
    CONFIRMATION_RESPONSE("confirmationResponse"),

    PRIVACY_POLICY_SOURCE("source"),
    TOS_SOURCE("source"),
    RESET_PASSWORD_LOCATION("resetLocation"),

    CALLING_VERSION("version"),
    CALLING_DIRECTION("direction"),
    CONVERSATION_TYPE("conversation_type"),
    CALLING_CONVERSATION_PARTICIPANTS("conversation_participants"),
    CALLING_CALL_PARTICIPANTS("call_participants"),
    CALLING_MAX_CALL_PARTICIPANTS("max_call_participants"),
    CALLING_END_REASON("reason"),
    CALLING_APP_IS_ACTIVE("app_is_active"),
    WITH_OTTO("with_otto"),
    WITH_BOT("with_bot"),
    IS_LAST_MESSAGE("is_last_message"),
    IS_EPHEMERAL("is_ephemeral"),
    EPHEMERAL_EXPIRATION("ephemeral_expiration"),

    FILE_SIZE_BYTES("file_size_bytes"),

    IS_RESENDING("is_resending"),
    FROM_SEARCH("from_search"),

    FIELD("field"),
    ACTION("action"),
    VIEW("view"),
    CONTEXT("context"),
    STATE("state"),
    DESCRIPTION("description"),
    SOURCE("source"),
    TARGET("target"),
    TYPE("type"),
    METHOD("method"),
    OUTCOME("outcome"),
    ERROR("error"),
    ERROR_MESSAGE("error_message"),
    VALUE("value"),
    USER("user"),
    EFFECT("effect"),
    LENGTH("length"),
    BY_USERNAME("by_username_only"),

    AVS("avs"),

    NAVIGATION_HINT_VISIBLE("hint_visible"),

    EXCEPTION_TYPE("exceptionType"),
    EXCEPTION_DETAILS("exceptionDetails"),

    // AN-4011: Temporary attributes
    REFERRAL_TOKEN("REFERRAL_TOKEN"),
    USER_TOKEN("USER_TOKEN"),
    SKETCH_SOURCE("sketch_source"),

    DAY("day"),
    MONTH("month"),
    YEAR("year"),

    GCM_SUCCESS("successful_gcm_notifications"),
    GCM_FAILED("failed_gcm_notifications"),
    GCM_RE_REGISTER("registration_retries"),
    TOTAL_PINGS("total_pings"),
    RECEIVED_PONGS("received_pongs"),
    PING_INTERVAL("ping_interval"),

    IS_EMPTY("is_empty"),
    WITH_SEARCH_RESULT("with_search_result")
    ;





    public final String name;

    Attribute(String name) {
        this.name = name;
    }
}
