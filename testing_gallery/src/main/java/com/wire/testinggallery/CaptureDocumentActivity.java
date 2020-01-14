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
package com.wire.testinggallery;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.wire.testinggallery.models.FileType;
import com.wire.testinggallery.models.Textfile;
import com.wire.testinggallery.utils.FileUtils;

public class CaptureDocumentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_chooser_popup);

        FileUtils.prepareSdCard(getResources(), FileUtils.TEST_FILE_TYPES.ALL, this.getApplicationContext());
        final String mime = getIntent().getType();

        if(mime == null)
            return;

        final FileType type = getFileTypeByMime(mime);
        if(type != null)
            if(type.getClass().equals(Textfile.class)) {
                addAvailableFileTypesButtons();
            } else
                type.handle(this);
        else
            finish();
    }

    private void addAvailableFileTypesButtons(){
        //the layout on which you are working
        final LinearLayout layout = this.findViewById(R.id.file_chooser_popup);
        for(FileType type : FileUtils.fileTypes) {
            //set the properties for button
            Button btn = new Button(this);
            btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setText(String.format(
                String.format(getString(R.string.file_chooser_popup_selection_string_format),
                    type.getName(), type.getExtension())));

            btn.setOnClickListener(v -> type.handle(this));
            btn.setContentDescription(String.format(getString(R.string.file_chooser_popup_selection_string_format), type.getName(), type.getExtension()));
            btn.setId(type.getPosition());
            //add button to the layout
            layout.addView(btn);
        }
    }

    /**
     * Getter for the correct FileType Class by its mime representive
     * @param mime
     * @return
     */
    private FileType getFileTypeByMime(String mime){
        for(FileType type : FileUtils.fileTypes) {
            if(type.getMimeType().startsWith(mime) || type.getMimeType().equals(mime)){
                //we found our FileType
                return type;
            }
        }
        return null;
    }
}

