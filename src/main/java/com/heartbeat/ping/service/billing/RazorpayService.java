package com.heartbeat.ping.service.billing;

import com.heartbeat.ping.config.properties.RazorpayProperties;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Thin adapter over the Razorpay SDK — the only place that makes external Razorpay API calls.
 * The client is built lazily from {@link RazorpayProperties} (the same code path for test and live;
 * only the env-supplied credentials differ), so an unconfigured dev/test context starts fine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final RazorpayProperties props;

    private volatile RazorpayClient client;

    /** Creates a Razorpay order and returns its id. Amount is in the smallest currency unit (paise). */
    public String createOrder(long amount, String currency, String receipt) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amount);
            request.put("currency", currency);
            request.put("receipt", receipt);
            Order order = client().orders.create(request);
            return order.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed", e);
            throw new BillingException("Could not create payment order, please try again");
        }
    }

    private RazorpayClient client() throws RazorpayException {
        RazorpayClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = new RazorpayClient(props.getKeyId(), props.getKeySecret());
                    client = c;
                }
            }
        }
        return c;
    }
}
