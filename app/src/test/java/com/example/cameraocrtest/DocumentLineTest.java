package com.example.cameraocrtest;

import com.example.cameraocrtest.data.DocumentLine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocumentLineTest {
    @Test
    public void lineUid_usesBlockAndLineIndex() {
        DocumentLine line = new DocumentLine(3, 7);
        assertEquals("b3_l7", line.GetLineUid());
    }
}
