/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.conversation

import com.waz.model.ConvId
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class AvatarViewTest extends JUnitSuite {

  @Test
  def pseudoRandomGeneration(): Unit ={
    val generator = ConversationAvatarView.RandomGeneratorFromConvId(ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    assert(generator.rand().toString() == "6505850725663318502")
  }

  @Test
  def stableRandomGeneration(): Unit ={
    val generator1 = ConversationAvatarView.RandomGeneratorFromConvId(ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    val generator2 = ConversationAvatarView.RandomGeneratorFromConvId(ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    assert(generator1.rand() == generator2.rand())
  }

  @Test
  def shuffleSeqWithRandomGenerator(): Unit ={
    val seq = Seq("a", "b", "c", "d", "e")
    val shuffled = ConversationAvatarView.shuffle(seq, ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    assert(shuffled == Seq("c", "e", "d", "b", "a"))
  }

  @Test
  def stableShuffleSeqWithRandomGenerator(): Unit ={
    val seq = Seq("a", "b", "c", "d", "e")
    val shuffled1 = ConversationAvatarView.shuffle(seq, ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    val shuffled2 = ConversationAvatarView.shuffle(seq, ConvId("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"))
    assert(shuffled1 == shuffled2)
  }
}
