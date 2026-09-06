package com.spendscope.categorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The merchant rule set, bound from {@code spendscope.categorization.rules} in
 * {@code application.yml}.
 *
 * <p>Order is significant: the first matching rule wins. That is what lets a
 * specific rule ("GRABFOOD" -> Food) sit in front of a general one
 * ("GRAB" -> Transport).
 */
@ConfigurationProperties(prefix = "spendscope.categorization")
public class CategorizationProperties {

    private List<MerchantRule> rules = new ArrayList<>();

    public List<MerchantRule> getRules() {
        return rules;
    }

    public void setRules(List<MerchantRule> rules) {
        this.rules = rules;
    }
}
