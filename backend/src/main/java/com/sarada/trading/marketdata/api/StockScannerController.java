package com.sarada.trading.marketdata.api;

import com.sarada.trading.marketdata.application.StockScannerService;
import com.sarada.trading.marketdata.application.StockScannerService.ScanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockScannerController {

    private final StockScannerService scannerService;

    @GetMapping("/gap-down-scanner")
    public ScanResponse getGapDownStocks() {
        return scannerService.getCachedResult();
    }

    @PostMapping("/gap-down-scanner/refresh")
    public ScanResponse refreshGapDownScan() {
        scannerService.triggerScan();
        return scannerService.getCachedResult();
    }
}
