package com.example.cameraocrtest.ner;

import com.example.cameraocrtest.data.SensitiveEntity;
import com.example.cameraocrtest.tokenization.koElectraTokenizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KoElectraNerEngine {
    private static final Set<String> DEFAULT_SENSITIVE_TAGS = new HashSet<>();

    static {
        DEFAULT_SENSITIVE_TAGS.add("PER");
        DEFAULT_SENSITIVE_TAGS.add("LOC");
        DEFAULT_SENSITIVE_TAGS.add("DAT");
        DEFAULT_SENSITIVE_TAGS.add("TIM");
        DEFAULT_SENSITIVE_TAGS.add("NUM");
        DEFAULT_SENSITIVE_TAGS.add("ORG");
        // Fallback regex labels
        DEFAULT_SENSITIVE_TAGS.add("PHONE");
        DEFAULT_SENSITIVE_TAGS.add("TEL");
        DEFAULT_SENSITIVE_TAGS.add("RRN");
        DEFAULT_SENSITIVE_TAGS.add("EMAIL");
        DEFAULT_SENSITIVE_TAGS.add("ACCOUNT");
        DEFAULT_SENSITIVE_TAGS.add("BUSINESS_NO");
        DEFAULT_SENSITIVE_TAGS.add("EMPLOYEE_ID");
        DEFAULT_SENSITIVE_TAGS.add("MNY");
    }

    public interface TokenProvider {
        List<String> getTokens(String text);
    }

    private static class RegexRule {
        private final Pattern pattern;
        private final String label;
        private final float confidence;

        private RegexRule(String regex, String label, float confidence) {
            this.pattern = Pattern.compile(regex);
            this.label = label;
            this.confidence = confidence;
        }
    }

    private static class KeywordValueRule {
        private final Pattern pattern;
        private final String label;
        private final float confidence;
        private final int valueGroup;

        private KeywordValueRule(String regex, String label, float confidence, int valueGroup) {
            this.pattern = Pattern.compile(regex);
            this.label = label;
            this.confidence = confidence;
            this.valueGroup = valueGroup;
        }
    }

    private static class SpanPrediction {
        private final int start;
        private final int end;
        private final String label;
        private final float confidence;

        private SpanPrediction(int start, int end, String label, float confidence) {
            this.start = start;
            this.end = end;
            this.label = label;
            this.confidence = confidence;
        }
    }

    private final TokenProvider tokenProvider;
    private final List<RegexRule> fallbackRules;
    private final List<KeywordValueRule> keywordValueRules;
    private final Set<String> sensitiveTags;

    public KoElectraNerEngine(koElectraTokenizer tokenizer) {
        this.tokenProvider = tokenizer::getTokens;
        this.fallbackRules = buildFallbackRules();
        this.keywordValueRules = buildKeywordValueRules();
        this.sensitiveTags = new HashSet<>(DEFAULT_SENSITIVE_TAGS);
    }

    public KoElectraNerEngine(koElectraTokenizer tokenizer, Set<String> sensitiveTags) {
        this.tokenProvider = tokenizer::getTokens;
        this.fallbackRules = buildFallbackRules();
        this.keywordValueRules = buildKeywordValueRules();
        this.sensitiveTags = (sensitiveTags == null || sensitiveTags.isEmpty())
                ? new HashSet<>(DEFAULT_SENSITIVE_TAGS)
                : new HashSet<>(sensitiveTags);
    }

    public KoElectraNerEngine(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        this.fallbackRules = buildFallbackRules();
        this.keywordValueRules = buildKeywordValueRules();
        this.sensitiveTags = new HashSet<>(DEFAULT_SENSITIVE_TAGS);
    }

    public List<SensitiveEntity> inferSensitiveEntities(String lineText) {
        List<String> tokens = tokenProvider.getTokens(lineText);
        List<TokenOffsetMapper.TokenSpan> tokenSpans = TokenOffsetMapper.mapTokensToOffsets(lineText, tokens);
        if (tokenSpans.isEmpty()) {
            return new ArrayList<>();
        }

        List<SpanPrediction> predictions = runInference(lineText, tokenSpans);
        String[] bioTags = new String[tokenSpans.size()];
        float[] confidences = new float[tokenSpans.size()];
        for (int i = 0; i < bioTags.length; i++) {
            bioTags[i] = "O";
            confidences[i] = 0.0f;
        }

        for (SpanPrediction prediction : predictions) {
            boolean started = false;
            for (int i = 0; i < tokenSpans.size(); i++) {
                TokenOffsetMapper.TokenSpan tokenSpan = tokenSpans.get(i);
                if (overlaps(tokenSpan.getStart(), tokenSpan.getEnd(), prediction.start, prediction.end)) {
                    if (!"O".equals(bioTags[i])) {
                        continue;
                    }
                    bioTags[i] = (started ? "I-" : "B-") + prediction.label;
                    confidences[i] = prediction.confidence;
                    started = true;
                }
            }
        }

        List<SensitiveEntity> merged = mergeBioTaggedTokens(lineText, tokenSpans, bioTags, confidences);
        return filterSensitiveEntities(merged, sensitiveTags);
    }

    private List<SpanPrediction> runInference(String lineText, List<TokenOffsetMapper.TokenSpan> tokenSpans) {
        List<SpanPrediction> predictions = new ArrayList<>();
        List<SpanPrediction> fallbackPredictions = new ArrayList<>();
        for (RegexRule rule : fallbackRules) {
            Matcher matcher = rule.pattern.matcher(lineText);
            while (matcher.find()) {
                fallbackPredictions.add(new SpanPrediction(matcher.start(), matcher.end(), rule.label, rule.confidence));
            }
        }
        for (KeywordValueRule rule : keywordValueRules) {
            Matcher matcher = rule.pattern.matcher(lineText);
            while (matcher.find()) {
                if (rule.valueGroup > matcher.groupCount()) {
                    continue;
                }
                String value = matcher.group(rule.valueGroup);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                int start = matcher.start(rule.valueGroup);
                int end = matcher.end(rule.valueGroup);
                if (start >= 0 && end > start) {
                    fallbackPredictions.add(new SpanPrediction(start, end, rule.label, rule.confidence));
                }
            }
        }

        if (predictions.isEmpty()) {
            predictions.addAll(fallbackPredictions);
        } else {
            for (SpanPrediction fallback : fallbackPredictions) {
                if (!hasOverlapWithSensitive(predictions, fallback, sensitiveTags)) {
                    predictions.add(fallback);
                }
            }
        }
        predictions.sort(Comparator.comparingInt(s -> s.start));
        return predictions;
    }

    private List<SensitiveEntity> mergeBioTaggedTokens(
            String lineText,
            List<TokenOffsetMapper.TokenSpan> tokenSpans,
            String[] bioTags,
            float[] confidences
    ) {
        List<SensitiveEntity> merged = new ArrayList<>();
        int i = 0;
        while (i < bioTags.length) {
            String tag = bioTags[i];
            if (!tag.startsWith("B-")) {
                i++;
                continue;
            }

            String label = tag.substring(2);
            int start = tokenSpans.get(i).getStart();
            int end = tokenSpans.get(i).getEnd();
            float confidence = confidences[i];
            int j = i + 1;

            while (j < bioTags.length && bioTags[j].equals("I-" + label)) {
                end = tokenSpans.get(j).getEnd();
                confidence = Math.max(confidence, confidences[j]);
                j++;
            }

            if (start >= 0 && end <= lineText.length() && start < end) {
                String value = lineText.substring(start, end);
                merged.add(new SensitiveEntity(value, label, start, end, confidence));
            }
            i = j;
        }
        return merged;
    }

    private static boolean overlaps(int aStart, int aEnd, int bStart, int bEnd) {
        return aStart < bEnd && bStart < aEnd;
    }

    private static boolean hasOverlapWithSensitive(List<SpanPrediction> predictions, SpanPrediction candidate, Set<String> sensitiveTags) {
        for (SpanPrediction prediction : predictions) {
            String normalized = normalizeLabel(prediction.label);
            if (!sensitiveTags.contains(normalized)) {
                continue;
            }
            if (overlaps(prediction.start, prediction.end, candidate.start, candidate.end)) {
                return true;
            }
        }
        return false;
    }

    private static List<RegexRule> buildFallbackRules() {
        List<RegexRule> rules = new ArrayList<>();
        rules.add(new RegexRule("\\b01[0-9]-?\\d{3,4}-?\\d{4}\\b", "PHONE", 0.93f));
        rules.add(new RegexRule("\\b[0-9]{2,3}-?[0-9]{3,4}-?[0-9]{4}\\b", "TEL", 0.89f));
        rules.add(new RegexRule("\\b[0-9]{6}-?[1-4][0-9]{6}\\b", "RRN", 0.97f));
        rules.add(new RegexRule("\\b\\d{3}-\\d{2}-\\d{5}\\b", "BUSINESS_NO", 0.96f));
        rules.add(new RegexRule("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "EMAIL", 0.95f));
        rules.add(new RegexRule("\\b\\d{2,3}-\\d{2,6}-\\d{2,6}\\b", "ACCOUNT", 0.86f));
        rules.add(new RegexRule("\\b\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\b", "DAT", 0.88f));
        rules.add(new RegexRule("\\b\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}\\b", "DAT", 0.86f));
        rules.add(new RegexRule("\\b(시급|월급|연봉)\\s*[:：]?\\s*\\d{1,3}(,\\d{3})*(원)?\\b", "MNY", 0.82f));
        return rules;
    }

    private static List<KeywordValueRule> buildKeywordValueRules() {
        List<KeywordValueRule> rules = new ArrayList<>();
        // 근로자/대표자 성명
        rules.add(new KeywordValueRule("(?i)(?:근로자\\s*성명|근로자명|성명|이름|대표자)\\s*[:：]?\\s*([가-힣]{2,5})", "PER", 0.92f, 1));
        // 생년월일
        rules.add(new KeywordValueRule("(?i)(?:생년월일|출생일)\\s*[:：]?\\s*(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{6})", "DAT", 0.91f, 1));
        // 주소(지번/도로명 포함 문장 일부)
        rules.add(new KeywordValueRule("(?i)(?:주소|현주소|거주지)\\s*[:：]?\\s*([가-힣0-9\\-\\s]{6,})", "LOC", 0.90f, 1));
        // 연락처
        rules.add(new KeywordValueRule("(?i)(?:연락처|휴대전화|전화번호|비상연락망)\\s*[:：]?\\s*(01[0-9]-?\\d{3,4}-?\\d{4}|0\\d{1,2}-?\\d{3,4}-?\\d{4})", "PHONE", 0.95f, 1));
        // 주민등록번호
        rules.add(new KeywordValueRule("(?i)(?:주민등록번호|주민번호|주민등록)\\s*[:：]?\\s*(\\d{6}-?[1-4]\\d{6})", "RRN", 0.98f, 1));
        // 이메일
        rules.add(new KeywordValueRule("(?i)(?:이메일|e-?mail)\\s*[:：]?\\s*([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", "EMAIL", 0.95f, 1));
        // 계좌번호
        rules.add(new KeywordValueRule("(?i)(?:계좌번호|입금계좌|급여계좌)\\s*[:：]?\\s*([0-9\\-]{8,24})", "ACCOUNT", 0.92f, 1));
        // 사업자등록번호
        rules.add(new KeywordValueRule("(?i)(?:사업자등록번호|사업자번호)\\s*[:：]?\\s*(\\d{3}-\\d{2}-\\d{5})", "BUSINESS_NO", 0.97f, 1));
        // 사번/직원번호(개인식별자로 취급 가능)
        rules.add(new KeywordValueRule("(?i)(?:사번|직원번호|근로자번호)\\s*[:：]?\\s*([A-Za-z0-9\\-]{4,20})", "EMPLOYEE_ID", 0.85f, 1));
        return rules;
    }

    private static List<SensitiveEntity> filterSensitiveEntities(List<SensitiveEntity> entities, Set<String> sensitiveTags) {
        List<SensitiveEntity> filtered = new ArrayList<>();
        for (SensitiveEntity entity : entities) {
            String normalizedLabel = normalizeLabel(entity.getLabel());
            if (sensitiveTags.contains(normalizedLabel)) {
                filtered.add(entity);
            }
        }
        return filtered;
    }

    private static String normalizeLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isEmpty()) {
            return "";
        }
        if (rawLabel.startsWith("B-") || rawLabel.startsWith("I-")) {
            return rawLabel.substring(2);
        }
        return rawLabel;
    }
}
