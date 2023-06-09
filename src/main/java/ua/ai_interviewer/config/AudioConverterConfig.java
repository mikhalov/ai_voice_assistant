package ua.ai_interviewer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.ai_interviewer.converter.AudioConverter;
import ua.ai_interviewer.converter.CustomFFMPEGLocator;
import ws.schild.jave.Encoder;
import ws.schild.jave.process.ProcessLocator;

@Configuration
public class AudioConverterConfig {

    @Bean
    public ProcessLocator processLocator() {
        return new CustomFFMPEGLocator("/usr/bin/ffmpeg");
    }

    @Bean
    public Encoder encoder() {
        return new Encoder(processLocator());
    }

    @Bean
    public AudioConverter audioConverter() {
        return new AudioConverter(processLocator(), encoder());
    }

}
