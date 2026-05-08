package com.meme.gdg.controller;

import com.meme.gdg.dto.BattlePairResponse;
import com.meme.gdg.service.QuickBattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/battle/quick")
public class QuickBattleController {

    private final QuickBattleService quickBattleService;

    @Autowired
    public QuickBattleController(QuickBattleService quickBattleService) {
        this.quickBattleService = quickBattleService;
    }

    @GetMapping("/pair")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BattlePairResponse> getNewPair() {
        BattlePairResponse pair = quickBattleService.getNewPair();
        return ResponseEntity.ok(pair);
    }
}
