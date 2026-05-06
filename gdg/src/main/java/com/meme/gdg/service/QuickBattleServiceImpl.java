package com.meme.gdg.service;

import com.meme.gdg.dto.BattlePairResponse;
import com.meme.gdg.dto.MemeSnapshot;
import com.meme.gdg.exception.InsufficientMemesException;
import com.meme.gdg.model.BattlePair;
import com.meme.gdg.model.Meme;
import com.meme.gdg.repository.BattlePairRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class QuickBattleServiceImpl implements QuickBattleService {

    private final MemeService memeService;
    private final BattlePairRepository battlePairRepository;

    @Autowired
    public QuickBattleServiceImpl(MemeService memeService, BattlePairRepository battlePairRepository) {
        this.memeService = memeService;
        this.battlePairRepository = battlePairRepository;
    }

    @Override
    public BattlePairResponse getNewPair() {
        List<Meme> memes;
        try {
            memes = memeService.getTwoRandomMemes();
        } catch (RuntimeException e) {
            throw new InsufficientMemesException("Insufficient memes for a battle");
        }

        if (memes == null || memes.size() < 2) {
            throw new InsufficientMemesException("Insufficient memes for a battle");
        }

        Meme memeA = memes.get(0);
        Meme memeB = memes.get(1);

        BattlePair battlePair = new BattlePair();
        battlePair.setMemeA(memeA);
        battlePair.setMemeB(memeB);
        BattlePair saved = battlePairRepository.save(battlePair);

        MemeSnapshot snapshotA = new MemeSnapshot(
                memeA.getId(),
                memeA.getTitle(),
                memeA.getImageUrl(),
                memeA.getVoteCount() != null ? memeA.getVoteCount() : 0
        );

        MemeSnapshot snapshotB = new MemeSnapshot(
                memeB.getId(),
                memeB.getTitle(),
                memeB.getImageUrl(),
                memeB.getVoteCount() != null ? memeB.getVoteCount() : 0
        );

        return new BattlePairResponse(saved.getId(), snapshotA, snapshotB);
    }
}
