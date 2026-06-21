package com.oculus.jj.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Smart Album rule engine - evaluates NSPredicate-style expressions against DicomStudy. */
public class AlbumRuleEngine {

    public enum MatchResult { MATCH, NO_MATCH, PARSE_ERROR }

    /** Single condition: field + operator + value. */
    public static class RuleCondition {
        public String field;
        public Operator operator;
        public Object value;

        public enum Operator { EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, GREATER_THAN, LESS_THAN, GREATER_EQUAL, LESS_EQUAL }

        public RuleCondition(String f, Operator op, Object v) { field = f; operator = op; value = parseValue(v); }

        private Object parseValue(Object val) {
            if (val == null) return null; String s = val.toString().trim();
            try { if (s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) return LocalDateTime.parse(s); } catch (DateTimeParseException ignored) {}
            try { if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(s).atStartOfDay(); } catch (DateTimeParseException ignored) {}
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            if ("true".equalsIgnoreCase(s)) return true; if ("false".equalsIgnoreCase(s)) return false;
            return s;
        }

        public boolean evaluate(DicomStudy study) {
            Object fv = getFieldValue(study); if (fv == null) return false;
            switch(operator) {
                case EQUALS:      return compare(fv, value) == 0;
                case NOT_EQUALS:  return compare(fv, value) != 0;
                case CONTAINS:    return stringContains(fv, value);
                case NOT_CONTAINS:return !stringContains(fv, value);
                case GREATER_THAN: return compare(fv, value) > 0;
                case LESS_THAN:   return compare(fv, value) < 0;
                case GREATER_EQUAL: return compare(fv, value) >= 0;
                case LESS_EQUAL:  return compare(fv, value) <= 0;
                default:          return false;
            }
        }

        private boolean stringContains(Object a, Object b) {
            String sa = a instanceof String ? (String)a : a.toString();
            String sb = b instanceof String ? (String)b : b.toString();
            return sa.toLowerCase().contains(sb.toLowerCase());
        }

        private int compare(Object a, Object b) {
            if (a instanceof LocalDateTime && b instanceof LocalDateTime) return ((LocalDateTime)a).compareTo((LocalDateTime)b);
            if (a instanceof LocalDate && b instanceof LocalDate) return ((LocalDate)a).compareTo((LocalDate)b);
            try { double da = a instanceof Number ? ((Number)a).doubleValue() : Double.parseDouble(a.toString());
                  double db = b instanceof Number ? ((Number)b).doubleValue() : Double.parseDouble(b.toString());
                  return Double.compare(da, db); } catch (NumberFormatException ignored) {}
            if (a instanceof String sa && b instanceof String sb) return sa.compareToIgnoreCase(sb);
            if (a == null && b == null) return 0; if (a == null) return -1; if (b == null) return 1;
            return a.toString().compareToIgnoreCase(b.toString());
        }

        private Object getFieldValue(DicomStudy study) { String f = field.trim().toLowerCase();
            try { switch(f) { case "patientname":     return study.getPatientName();
                case "modality":       return study.getModality();
                case "accessionnumber":return study.getAccessionNumber();
                case "studyinstanceuid":return study.getStudyInstanceUID();
                case "studyid":        return study.getStudyID();
                case "studyname":      return study.getStudyName();
                case "institutionname":return study.getInstitutionName();
                case "referringphysician":return study.getReferringPhysician();
                case "performingphysician":return study.getPerformingPhysician();
                case "patientsex":     return study.getPatientSex();
                case "dateofbirth":    return study.getDateOfBirth();
                case "dateopened":     return study.getDateOpened();
                default:               return study.getDate(); } } catch (Exception e) { return null; }
        }
    }

    /** Condition block - joins multiple conditions with AND/OR logic. */
    public static class RuleBlock {
        public enum LogicOp { AND, OR }
        public LogicOp logicOp = LogicOp.AND;
        public List<RuleCondition> conditions = new ArrayList<>();
        public List<RuleBlock> subBlocks = new ArrayList<>();

        public boolean evaluate(DicomStudy study) {
            if (conditions.isEmpty() && subBlocks.isEmpty()) return true;
            boolean first = false, hasC = false;
            for (RuleCondition c : conditions) {
                if (!hasC) { first = c.evaluate(study); hasC = true; } else first = apply(first, c.evaluate(study)); }
            for (RuleBlock sb : subBlocks) {
                if (!hasC) { first = sb.evaluate(study); hasC = true; } else first = apply(first, sb.evaluate(study)); }
            return hasC ? first : true;
        }
        private boolean apply(boolean a, boolean b) { return logicOp == LogicOp.AND ? (a && b) : (a || b); }
    }

    /** Rule set - multiple blocks joined by AND. */
    public static class RuleSet {
        public List<RuleBlock> blocks = new ArrayList<>();
        public boolean evaluate(DicomStudy study) { for (RuleBlock b : blocks) if (!b.evaluate(study)) return false; return true; }
    }

    // --- Parser ---
    public static RuleSet parsePredicate(String ps) {
        if (ps == null || ps.trim().isEmpty()) return new RuleSet();
        RuleSet rs = new RuleSet(); try { for (String p : splitByOr(ps.trim())) {
            RuleBlock b = parseConditionGroup(p.trim());
            if (!b.conditions.isEmpty() || !b.subBlocks.isEmpty()) rs.blocks.add(b); } } catch (Exception ignored) {} return rs;
    }

    private static List<String> splitByOr(String s) {
        List<String> parts = new ArrayList<>(); int d=0; StringBuilder sb=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i); if(c=='('){d++;sb.append(c);}else if(c==')'){d--;sb.append(c);}
            else if(c=='O'&&i+1<s.length()&&s.charAt(i+1)=='R'&&d==0){parts.add(sb.toString());sb.setLength(0);i++;}else sb.append(c);}
        if(sb.length()>0) parts.add(sb.toString()); return parts.isEmpty()?List.of(s):parts; }

    private static RuleBlock parseConditionGroup(String g) {
        if(g==null||g.trim().isEmpty()) return new RuleBlock(); RuleBlock b=new RuleBlock();
        for(String p:splitByAnd(g)){String t=p.trim(); if(t.isEmpty())continue;
            if(t.startsWith("(")&&t.endsWith(")")) b.subBlocks.add(parseConditionGroup(t.substring(1,t.length()-1)));
            else{RuleCondition c=parseCondition(t);if(c!=null)b.conditions.add(c);}} return b; }

    private static List<String> splitByAnd(String s) {
        List<String> parts=new ArrayList<>(); int d=0; StringBuilder sb=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i); if(c=='('){d++;sb.append(c);}else if(c==')'){d--;sb.append(c);}
            else if(c=='A'&&i+2<s.length()&&s.charAt(i+1)=='N'&&s.charAt(i+2)=='D'&&d==0){parts.add(sb.toString());sb.setLength(0);i+=2;}else sb.append(c);}
        if(sb.length()>0) parts.add(sb.toString()); return parts.isEmpty()?List.of(s):parts; }

    private static RuleCondition parseCondition(String cond) {
        if(cond==null||cond.trim().isEmpty()) return null; String t=cond.trim(); int idx=-1; String opStr="";
        for(String os:new String[]{">=","<=","==","!=","CONTAINS","NOT_CONTAINS","<",">"}) { int i=t.indexOf(os); if(i>0){idx=i;opStr=os;break;} }
        if(idx<0) return null; RuleCondition.Operator op; switch(opStr){ case "==":op=RuleCondition.Operator.EQUALS;break;
            case "!=":op=RuleCondition.Operator.NOT_EQUALS;break; case ">=":op=RuleCondition.Operator.GREATER_EQUAL;break;
            case "<=":op=RuleCondition.Operator.LESS_EQUAL;break; case ">":op=RuleCondition.Operator.GREATER_THAN;break;
            case "<":op=RuleCondition.Operator.LESS_THAN;break; case "CONTAINS":op=RuleCondition.Operator.CONTAINS;break;
            default: return null;} String raw=t.substring(idx+opStr.length()).trim();
        if((raw.startsWith("\"")&&raw.endsWith("\""))||(raw.startsWith("'")&&raw.endsWith("'"))) raw=raw.substring(1,raw.length()-1);
        return new RuleCondition(t.substring(0,idx).trim(), op, raw); }

    public static MatchResult matches(DicomStudy s, String ps) { if(s==null||ps==null||ps.trim().isEmpty()) return MatchResult.MATCH; try{return parsePredicate(ps).evaluate(s)?MatchResult.MATCH:MatchResult.NO_MATCH;}catch(Exception e){return MatchResult.PARSE_ERROR;} }
    public static List<DicomStudy> filterStudies(List<DicomStudy> studies, String ps) { if(studies==null||ps==null||ps.trim().isEmpty()) return new ArrayList<>(studies); RuleSet rs=parsePredicate(ps); List<DicomStudy>r=new ArrayList<>(); for(DicomStudy s:studies){if(rs.evaluate(s))r.add(s);} return r; }
}