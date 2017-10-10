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
package com.waz.zclient.controllers.stubs;

import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class StubUserPreferencesController implements IUserPreferencesController {
  @Override
  public int getLastAccentColor() {
    return 0;
  }

  @Override
  public boolean showContactsDialog() {
    return false;
  }

  @Override
  public void setVerificationCode(String code) {

  }

  @Override
  public String getVerificationCode() {
    return null;
  }

  @Override
  public boolean hasVerificationCode() {
    return false;
  }

  @Override
  public void setPostSessionIdToConversation(boolean postSessionIdToConversation) {

  }

  @Override
  public void reset() {

  }

  @Override
  public void setGenericInvitationToken(String token) {

  }

  @Override
  public String getLastCallSessionId() {
    return null;
  }

  @Override
  public void removeVerificationCode() {

  }

  @Override
  public void setPerformedAction(int action) {

  }

  @Override
  public void addRecentEmoji(String emoji) {

  }

  @Override
  public List<String> getRecentEmojis() {
    return null;
  }

  @Override
  public void setUnsupportedEmoji(Collection<String> emoji, int version) {

  }

  @Override
  public Set<String> getUnsupportedEmojis() {
    return null;
  }

  @Override
  public boolean hasCheckedForUnsupportedEmojis(int version) {
    return false;
  }

  @Override
  public void setLastAccentColor(int accentColor) {

  }

  @Override
  public String getGenericInvitationToken() {
    return null;
  }

  @Override
  public void tearDown() {

  }

  @Override
  public void setReferralToken(String token) {

  }

  @Override
  public String getDeviceId() {
    return null;
  }

  @Override
  public boolean isPostSessionIdToConversation() {
    return false;
  }

  @Override
  public boolean hasPerformedAction(int action) {
    return false;
  }

  @Override
  public String getReferralToken() {
    return null;
  }

  @Override
  public long getLastEphemeralValue() {
    return 0;
  }

  @Override
  public void setLastEphemeralValue(long value) {
  }

  @Override
  public boolean isVariableBitRateEnabled() {
    return false;
  }

}
