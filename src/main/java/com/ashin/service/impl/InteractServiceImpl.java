package com.ashin.service.impl;

import com.ashin.config.GptConfig;
import com.ashin.config.KeywordConfig;
import com.ashin.entity.bo.ChatBO;
import com.ashin.exception.ChatException;
import com.ashin.service.InteractService;
import com.ashin.util.BotUtil;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.image.CreateImageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 交互服务impl
 *
 * @author ashinnotfound
 * @date 2022/12/10
 */
@Service
@Slf4j
public class InteractServiceImpl implements InteractService {

    @Resource
    private BotUtil botUtil;
    @Resource
    private KeywordConfig keywordConfig;
    @Resource
    private GptConfig gptConfig;

    @Override
    public String chat(ChatBO chatBO) throws ChatException {

        List<ChatMessage> prompt = botUtil.buildPrompt(chatBO.getSessionId(), chatBO.getPrompt());
        if (prompt == null) {
            throw new ChatException("提问失败: 提问内容过长");
        }

        ChatMessage answer = null;
        try {
            //向gpt提问
            if (chatBO.isAiDraw()) {
                String description = chatBO.getPrompt().replaceFirst(keywordConfig.getDraw() + " ", "");
                CreateImageRequest createImageRequest = CreateImageRequest.builder().n(1).size("1024x1024").prompt(description).model("dall-e-3").quality(gptConfig.getImageQuality()).style(gptConfig.getImageStyle()).build();
                answer = new ChatMessage();
                answer.setRole("assistant");
                answer.setContent(botUtil.getOpenAiService().createImage(createImageRequest).getData().get(0).getUrl());
            } else {
                ChatCompletionRequest completionRequest = botUtil.getCompletionRequestBuilder().messages(prompt).build();
                answer = botUtil.getOpenAiService().createChatCompletion(completionRequest).getChoices().get(0).getMessage();
            }
        } catch (OpenAiHttpException e) {
            log.error("向gpt提问失败，提问内容：{}，\n原因：{}\n", chatBO.getPrompt(), e.getMessage(), e);
            // https://platform.openai.com/docs/guides/error-codes/api-errors
            if (400 == e.statusCode) {
                throw new ChatException("提问失败: 你的提问被OPENAI安全系统拒绝，请检查是否有敏感词等");
            } else if (401 == e.statusCode) {
                throw new ChatException("提问失败: 无效的apikey, 请确保apikey正确且你拥有权限");
            } else if (429 == e.statusCode) {
                throw new ChatException("提问失败: 提问过于频繁 或者 apikey余额不足");
            } else if (500 == e.statusCode) {
                throw new ChatException("提问失败: openai服务异常, 请稍后重试");
            } else if (503 == e.statusCode) {
                throw new ChatException("提问失败: openai服务过载, 请稍后重试");
            }
        } catch (RuntimeException e) {
            throw new ChatException("提问失败: 大概率网络异常, 具体: " + e.getMessage());
        }
        if (null == answer) {
            throw new ChatException("提问失败: 未知错误, 若频繁出现请提issue");
        }

        prompt.add(answer);

        return answer.getContent().trim();
    }
}
