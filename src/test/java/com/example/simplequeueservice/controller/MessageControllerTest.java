package com.example.simplequeueservice.controller;

import com.example.simplequeueservice.model.Message;
import com.example.simplequeueservice.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Test
    @WithMockUser(username = "user", password = "password", roles = "USER")
    public void testPush() throws Exception {
        String jsonContent = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        when(messageService.push(anyString(), anyString())).thenReturn(new Message(jsonContent));

        mockMvc.perform(post("/queue/push")
                .header("consumerGroup", "testGroup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testPushAsAdmin() throws Exception {
        String jsonContent = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        when(messageService.push(anyString(), anyString())).thenReturn(new Message(jsonContent));

        mockMvc.perform(post("/queue/push")
                .header("consumerGroup", "testGroup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent)
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", password = "password", roles = "USER")
    public void testPop() throws Exception {
        when(messageService.pop(anyString())).thenReturn(Optional.of(new Message("Test message")));

        mockMvc.perform(get("/queue/pop")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testPopAsAdmin() throws Exception {
        when(messageService.pop(anyString())).thenReturn(Optional.of(new Message("Test message")));

        mockMvc.perform(get("/queue/pop")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", password = "password", roles = "USER")
    public void testViewForbiddenForUser() throws Exception {
        when(messageService.view(anyString())).thenReturn(Arrays.asList(new Message("Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", password = "adminpassword", roles = {"ADMIN", "USER"})
    public void testViewAsAdmin() throws Exception {
        when(messageService.view(anyString())).thenReturn(Arrays.asList(new Message("Test message")));

        mockMvc.perform(get("/queue/view")
                .header("consumerGroup", "testGroup"))
                .andExpect(status().isOk());
    }
}
