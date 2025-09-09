package ru.javaboys.huntyhr.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.jmix.email.Emailer;

@Service
public class JavaBoysEmailSendingJob {

    @Autowired
    private Emailer emailer;

    @Scheduled(fixedRate = 60 * 1000)
    public void execute() {
        emailer.processQueuedEmails();
    }

}