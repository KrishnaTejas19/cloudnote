package com.cloudnote;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cloudnote.local-dir=target/test-uploads")
class NoteApiTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long createNote() throws Exception {
        String body = mvc.perform(post("/api/notes").contentType("application/json")
                        .content("{\"title\":\"Hello\",\"content\":\"# World\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        return n.get("id").asLong();
    }

    @Test
    void crudRoundTrip() throws Exception {
        long id = createNote();
        mvc.perform(get("/api/notes/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello"));
        mvc.perform(put("/api/notes/" + id).contentType("application/json")
                        .content("{\"title\":\"Edited\",\"content\":\"x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Edited"));
        mvc.perform(get("/api/notes")).andExpect(status().isOk());
        mvc.perform(delete("/api/notes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        mvc.perform(post("/api/notes").contentType("application/json")
                        .content("{\"title\":\" \",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAndDownloadTextFile() throws Exception {
        long id = createNote();
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hi there".getBytes());
        String body = mvc.perform(multipart("/api/notes/" + id + "/attachments").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fileId = json.readTree(body).get("id").asLong();

        mvc.perform(get("/api/notes/" + id + "/attachments/" + fileId))
                .andExpect(status().isOk())
                .andExpect(content().string("hi there"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void rejectsDisallowedExtensionAndFakeImage() throws Exception {
        long id = createNote();
        mvc.perform(multipart("/api/notes/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/notes/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "not a png".getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
