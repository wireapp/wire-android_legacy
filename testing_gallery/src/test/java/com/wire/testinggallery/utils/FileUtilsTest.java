package com.wire.testinggallery.utils;

import com.wire.testinggallery.UnitTest;
import com.wire.testinggallery.models.Audio;
import com.wire.testinggallery.models.Backup;
import com.wire.testinggallery.models.FileType;
import com.wire.testinggallery.models.Image;
import com.wire.testinggallery.models.PlainText;
import com.wire.testinggallery.models.Textfile;
import com.wire.testinggallery.models.Video;

import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilsTest extends UnitTest {

    @Test
    public void shouldSupportTheRightFileTypes() {
        List<FileType> fileTypes = FileUtils.fileTypes;

        assertThat(fileTypes.get(0)).isExactlyInstanceOf(Textfile.class);
        assertThat(fileTypes.get(1)).isExactlyInstanceOf(Video.class);
        assertThat(fileTypes.get(2)).isExactlyInstanceOf(Audio.class);
        assertThat(fileTypes.get(3)).isExactlyInstanceOf(Image.class);
        assertThat(fileTypes.get(4)).isExactlyInstanceOf(Backup.class);
        assertThat(fileTypes.get(5)).isExactlyInstanceOf(PlainText.class);
        assertThat(fileTypes.size()).isEqualTo(6);
    }
}
