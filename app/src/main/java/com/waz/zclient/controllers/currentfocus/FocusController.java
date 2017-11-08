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
package com.waz.zclient.controllers.currentfocus;

import android.content.Context;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import timber.log.Timber;

public class FocusController implements IFocusController {

    @FocusLocation private int currentFocus;


    public FocusController() {
    }

    @Override
    public void setFocus(@FocusLocation int currentFocus) {
        Timber.i("Changing focus to %s", currentFocus);
        this.currentFocus = currentFocus;
    }

    @Override
    public @FocusLocation int getCurrentFocus() {
        return currentFocus;
    }

    @Override
    public void restoreToNextFocus(Context context,
                                   int pagerPosition,
                                   Page currentPage,
                                   boolean conversationlistSearchIsOpen) {
        if (LayoutSpec.isPhone(context)) {
            return;
        }

        if (ViewUtils.isInPortrait(context) &&
            pagerPosition == 0 &&
            conversationlistSearchIsOpen) {
            setFocus(IFocusController.CONVERSATION_LIST_SEARCHBOX);
            return;
        }

        setFocus(IFocusController.CONVERSATION_CURSOR);
    }
}
