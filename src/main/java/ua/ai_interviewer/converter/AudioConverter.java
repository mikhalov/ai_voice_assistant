package ua.ai_interviewer.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.InputFormatException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.process.ProcessLocator;

import java.io.File;

@Slf4j
@RequiredArgsConstructor
public class AudioConverter {

    private final ProcessLocator locator;
    private final Encoder encoder;


    public File convertToMp3(File source, String targetName) {
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        audio.setBitRate(128000);
        audio.setChannels(2);
        audio.setSamplingRate(44100);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);
        log.debug("Set custom ffmpeg location ");


        File file = new File(targetName + ".mp3");
        try {
            encoder.encode(new MultimediaObject(source, locator), file, attrs);
            log.debug("Successful converted and create new file {}", file.getAbsolutePath());
        } catch (InputFormatException e) {
            log.error("The source multimedia file cannot be decoded.", e);
        } catch (EncoderException e) {
            log.error("Problems occurs during the encoding process", e);
        }

        return file;
    }
}