package io.streamvault.core.api.admin.dto;

import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

public class UploadForm {

    @RestForm("files")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public List<FileUpload> files;
}
