package ua.ai_interviewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.ai_interviewer.converter.AudioConverter;
import ua.ai_interviewer.converter.CustomFFMPEGLocator;
import ws.schild.jave.Encoder;
import ws.schild.jave.process.ProcessLocator;

@Configuration
public class AudioConverterConfig {
    @Value("${ffmpeg.path.windows}")
    private String winPath;
    @Value("${ffmpeg.path.linux}")
    private String linPath;


    @Bean
    public ProcessLocator processLocator() {
        String currentOsPath = switch (System.getProperty("os.name").toLowerCase()) {
            case String os when os.contains("win") -> winPath;
            case String os when os.contains("nix") || os.contains("nux") || os.contains("aix") -> linPath;
            default -> throw new IllegalStateException("App has not been ran on win or linux machine");
        };

        return new CustomFFMPEGLocator(currentOsPath);
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
