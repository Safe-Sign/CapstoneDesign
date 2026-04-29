package com.example.cameraocrtest;

import com.example.cameraocrtest.data.SensitiveEntity;
import com.example.cameraocrtest.ner.KoElectraNerEngine;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KoElectraNerEngineTest {
    @Test
    public void inferSensitiveEntities_detectsEmailAndPhone() {
        KoElectraNerEngine engine = new KoElectraNerEngine(text -> Arrays.asList(
                "[CLS]", "연락", "##처", "는", "hong", "@", "example", ".", "com", "과", "010", "-", "1234", "-", "5678", "[SEP]"
        ));

        List<SensitiveEntity> entities = engine.inferSensitiveEntities("연락처는 hong@example.com과 010-1234-5678");
        assertEquals(2, entities.size());
        assertEquals("EMAIL", entities.get(0).getLabel());
        assertEquals("hong@example.com", entities.get(0).getValue());
        assertEquals("PHONE", entities.get(1).getLabel());
        assertEquals("010-1234-5678", entities.get(1).getValue());
        assertTrue(entities.get(0).getStart() < entities.get(0).getEnd());
        assertTrue(entities.get(1).getStart() < entities.get(1).getEnd());
    }
}
