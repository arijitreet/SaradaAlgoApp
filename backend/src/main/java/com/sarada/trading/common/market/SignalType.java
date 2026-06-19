package com.sarada.trading.common.market;

public enum SignalType {
    BUY_CE,
    BUY_PE;

    public String optionType() {
        return this == BUY_CE ? "CE" : "PE";
    }
}
