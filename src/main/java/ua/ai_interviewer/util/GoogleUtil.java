package ua.ai_interviewer.util;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.ai_interviewer.enums.Language;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalTime;

/**
 * Utility class for processing text-to-speech queries via Google Text-to-Speech API.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GoogleUtil {

    /**
     * Converts text to speech, and saves the audio to a file.
     *
     * @param text     The text to be converted.
     * @param language The language of the text.
     * @return The file with the audio content.
     * @throws IOException If an I/O error occurs.
     */
    public static File textToFile(String text, Language language) throws IOException {
        log.debug("started call to google api");
        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

            VoiceSelectionParams voice =
                    VoiceSelectionParams.newBuilder()
                            .setLanguageCode(language.getCode())
                            .setName(language.getName())
                            .build();

            AudioConfig audioConfig =
                    AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.OGG_OPUS).build();
            log.trace("before request");
            SynthesizeSpeechResponse response =
                    textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);
            log.debug("Successful got response");
            ByteString audioContents = response.getAudioContent();

            int nano = LocalTime.now().getNano();
            int hash = text.hashCode();
            File file = new File(hash - nano + ".ogg");
            log.debug("try to save in file");
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(audioContents.toByteArray());
                log.debug("Audio content written to file {}", file.getName());

            }
            return file;
        }
    }

}
