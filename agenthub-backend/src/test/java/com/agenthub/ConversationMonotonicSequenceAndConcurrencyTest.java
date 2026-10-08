package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.service.ConversationSequenceManager;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConversationMonotonicSequenceAndConcurrencyTest {

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private ConversationSequenceManager sequenceManager;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("测试消息序列单调定序：连续发送消息时 sequence_num 严格单调递增无间隙")
    void shouldEnsureStrictlyMonotonicSequenceNum() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "单调序号会话",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        for (int i = 1; i <= 5; i++) {
            MessageView msg = conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                    "user-1",
                    SenderType.USER,
                    "指令 " + i
            ));
            assertThat(msg.getSequenceNum()).isEqualTo((long) i);
        }

        List<MessageView> messages = conversationApplication.listMessages(conv.getId());
        assertThat(messages).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(messages.get(i).getSequenceNum()).isEqualTo((long) (i + 1));
        }
    }

    @Test
    @DisplayName("测试高并发序号分配安全：20 个并发线程同时请求序号，保证零重复、零乱序、全单调")
    void shouldAllocateMonotonicSequencesConcurrentlyWithoutCollisions() throws Exception {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "并发定序测试会话",
                ConversationType.GROUP_COLLABORATION,
                List.of("BackendArchitect", "FrontendEngineer"),
                "proj-default"
        ));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // ensure simultaneous execution
                return sequenceManager.nextSequenceNum(conv.getId());
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Long> allocatedSeqs = new ArrayList<>();
        for (Future<Long> f : futures) {
            allocatedSeqs.add(f.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // Verify distinctness & ordering
        Set<Long> uniqueSeqs = new HashSet<>(allocatedSeqs);
        assertThat(uniqueSeqs).hasSize(threadCount);

        List<Long> sorted = new ArrayList<>(allocatedSeqs);
        Collections.sort(sorted);

        // Should be exactly 1 to 20
        List<Long> expected = new ArrayList<>();
        for (long i = 1; i <= threadCount; i++) {
            expected.add(i);
        }
        assertThat(sorted).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("测试按 sinceSeq 增量查询：断点续拉仅获取 sequence_num 大于游标的消息")
    void shouldFetchMessagesSinceSequenceCursor() {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "游标分页会话",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        for (int i = 1; i <= 6; i++) {
            conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                    "user-1", SenderType.USER, "消息 #" + i
            ));
        }

        // Fetch sinceSeq = 3 -> should return messages 4, 5, 6
        List<MessageView> incremental = conversationApplication.listMessages(conv.getId(), 3L, "user-1");
        assertThat(incremental).hasSize(3);
        assertThat(incremental.stream().map(MessageView::getSequenceNum).collect(Collectors.toList()))
                .containsExactly(4L, 5L, 6L);
    }

    @Test
    @DisplayName("测试全链路并发消息发送定序：10 个并发线程同时调用 sendMessage，序列号绝对无重复无空洞")
    void shouldAllocateSequencesConcurrentlyViaSendMessageWithoutCollisions() throws Exception {
        ConversationView conv = conversationApplication.createConversation(new CreateConversationCommand(
                "并发SendMessage会话",
                ConversationType.DIRECT_CHAT,
                List.of("BackendArchitect"),
                "proj-default"
        ));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MessageView>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                RequestContext.get().setUserId("user-1");
                readyLatch.countDown();
                startLatch.await();
                return conversationApplication.sendMessage(conv.getId(), new SendMessageCommand(
                        "user-1", SenderType.USER, "并发需求 " + index
                ));
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<Long> sequenceNums = new ArrayList<>();
        for (Future<MessageView> f : futures) {
            sequenceNums.add(f.get(10, TimeUnit.SECONDS).getSequenceNum());
        }

        executor.shutdown();

        Set<Long> uniqueSeqs = new HashSet<>(sequenceNums);
        assertThat(uniqueSeqs).hasSize(threadCount);

        List<Long> sorted = new ArrayList<>(sequenceNums);
        Collections.sort(sorted);
        List<Long> expected = new ArrayList<>();
        for (long i = 1; i <= threadCount; i++) {
            expected.add(i);
        }
        assertThat(sorted).containsExactlyElementsOf(expected);

        ConversationView updatedConv = conversationApplication.getConversationById(conv.getId());
        assertThat(updatedConv.getLastSequenceNum()).isEqualTo((long) threadCount);
    }
}
