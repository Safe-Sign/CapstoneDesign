package com.example.cameraocrtest.domain.model;

import com.example.cameraocrtest.data.DocumentWord;

public class ProperNounHit {
    public final int sequenceNumber;
    public final String origin;
    public final DocumentWord sourceInfo;

    public ProperNounHit(int sequenceNumber, String origin, DocumentWord sourceInfo) {
        this.origin = origin;
        this.sequenceNumber = sequenceNumber;
        this.sourceInfo = sourceInfo;
    }

}
