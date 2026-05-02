package com.example.cameraocrtest.data;

public class ResponseData
{
    public final int blockIdx;
    public final int sentenceIdx;
    public final int state;
    public final String reason;
    public final String law;
    public final String action;

    public ResponseData(int blockIdx,int sentenceIdx,int state,String reason,String law,String action)
    {
        this.blockIdx = blockIdx;
        this.sentenceIdx = sentenceIdx;
        this.state = state;
        this.reason = reason;
        this.law = law;
        this.action = action;

    }

}
