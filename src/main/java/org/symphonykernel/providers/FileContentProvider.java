package org.symphonykernel.providers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;


@Component
public class FileContentProvider {

    public String loadFileContent(String filePath) throws IOException {
        Path path = ResourceUtils.getFile(filePath).toPath();
        return new String(Files.readAllBytes(path));
    }
}
