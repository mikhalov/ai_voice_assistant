package ua.ai_interviewer.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.InputFormatException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;

@Slf4j
@Component
public class AudioConverter {

    public File convertToMp3(File source, String targetName) {
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        audio.setBitRate(128000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);

        Encoder encoder = new Encoder();
        File file = new File(targetName + ".mp3");
        try {
            encoder.encode(new MultimediaObject(source), file, attrs);
            log.debug("Successful converted and create new file {}", file.getAbsolutePath());
            log.debug("Successful deleted source file");
        } catch (InputFormatException e) {
            log.error("The source multimedia file cannot be decoded.", e);
        } catch (EncoderException e) {
            log.error("Problems occurs during the encoding process", e);
        }

        return file;
    }
}