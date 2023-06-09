package ua.ai_interviewer.converter;

import ws.schild.jave.process.ProcessLocator;
import ws.schild.jave.process.ProcessWrapper;
import ws.schild.jave.process.ffmpeg.FFMPEGProcess;

public class CustomFFMPEGLocator implements ProcessLocator {

    private final String ffmpegExecutablePath;

    public CustomFFMPEGLocator(String ffmpegExecutablePath) {
        this.ffmpegExecutablePath = ffmpegExecutablePath;
    }

    @Override
    public String getExecutablePath() {
        return ffmpegExecutablePath;
    }

    @Override
    public ProcessWrapper createExecutor() {
        return new FFMPEGProcess(getExecutablePath());
    }
}