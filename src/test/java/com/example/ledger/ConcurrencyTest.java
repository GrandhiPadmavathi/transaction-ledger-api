package com.example.ledger;

import com.example.ledger.account.AccountService;
import com.example.ledger.common.error.ApiException;
import com.example.ledger.common.error.ErrorCode;
import com.example.ledger.transaction.TransactionService;
import com.example.ledger.transaction.dto.CreateTransactionRequest;
import com.example.ledger.transfer.TransferService;
import com.example.ledger.transfer.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    private static final int THREADS = 16;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    @Test
    void concurrentDebitsNeverOverdrawTheAccount() throws Exception {
        String id = accountService.create("Concurrent").getId();
        transactionService.record(id, request("credit", "100", "seed"));

        List<ErrorCode> outcomes = runConcurrently(40, i -> () -> {
            transactionService.record(id, request("debit", "10", "debit-" + i));
            return null;
        });

        assertThat(outcomes.stream().filter(o -> o == null).count()).isEqualTo(10);
        assertThat(outcomes.stream().filter(o -> o == ErrorCode.INSUFFICIENT_BALANCE).count()).isEqualTo(30);
        assertThat(accountService.get(id).getBalance()).isEqualByComparingTo("0");
        assertThat(transactionService.history(id, "debit", 1, 100).getTotalElements()).isEqualTo(10);
    }

    @Test
    void concurrentDuplicatesCreateExactlyOneTransaction() throws Exception {
        String id = accountService.create("Duplicates").getId();

        List<ErrorCode> outcomes = runConcurrently(20, i -> () -> {
            transactionService.record(id, request("credit", "25", "same-ref"));
            return null;
        });

        assertThat(outcomes.stream().filter(o -> o == null).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o == ErrorCode.DUPLICATE_TRANSACTION).count()).isEqualTo(19);
        assertThat(accountService.get(id).getBalance()).isEqualByComparingTo("25");
    }

    @Test
    void opposingConcurrentTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        String a = accountService.create("A").getId();
        String b = accountService.create("B").getId();
        transactionService.record(a, request("credit", "500", "seed-a"));
        transactionService.record(b, request("credit", "500", "seed-b"));

        List<ErrorCode> outcomes = runConcurrently(40, i -> () -> {
            boolean forward = i % 2 == 0;
            transferService.transfer(transfer(forward ? a : b, forward ? b : a, "5", "t-" + i));
            return null;
        });

        assertThat(outcomes).containsOnlyNulls();
        BigDecimal total = accountService.get(a).getBalance().add(accountService.get(b).getBalance());
        assertThat(total).isEqualByComparingTo("1000");
        assertThat(accountService.get(a).getBalance()).isEqualByComparingTo("500");
    }

    private interface TaskFactory {
        Callable<Void> create(int index);
    }

    /** Runs the tasks concurrently and returns, per task, null on success or the ApiException code. */
    private List<ErrorCode> runConcurrently(int count, TaskFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Void> task = factory.create(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (ApiException ex) {
                        return ex.getCode();
                    }
                }));
            }
            start.countDown();
            List<ErrorCode> outcomes = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static CreateTransactionRequest request(String type, String amount, String referenceId) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setReferenceId(referenceId);
        return request;
    }

    private static TransferRequest transfer(String from, String to, String amount, String referenceId) {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(from);
        request.setToAccountId(to);
        request.setAmount(new BigDecimal(amount));
        request.setReferenceId(referenceId);
        return request;
    }
}
