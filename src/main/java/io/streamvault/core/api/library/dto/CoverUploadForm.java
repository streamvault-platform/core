package io.streamvault.core.api.library.dto;

import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class CoverUploadForm {

    @RestForm("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public FileUpload file;
}
