package com.oculus.jj.model;

import java.util.HashSet;
import java.util.Set;
import java.util.*;

/** DicomAlbum - Album entity. Ported from HOROS-20240407/Sources/DicomAlbum.h. */
public class DicomAlbum implements Comparable<DicomAlbum> {
    
    private Integer index;
    private String name;
    private String predicateString;
    private Boolean smartAlbum = false;
    private Set<DicomStudy> studies = new HashSet<>();
    private int numberOfStudies = 0;
    
    public DicomAlbum() {}
    
    public DicomAlbum(String name) { this.name = name; }
    
    public DicomAlbum(String name, String predicateString) {
        this.name = name; this.predicateString = predicateString; this.smartAlbum = true;
    }
    
    public void addStudiesObject(DicomStudy study) {
        if (study != null) { studies.add(study); numberOfStudies = studies.size(); }
    }
    
    public void removeStudiesObject(DicomStudy study) {
        if (study != null) { studies.remove(study); numberOfStudies = studies.size(); }
    }
    
    public void addStudies(Set<DicomStudy> ss) {
        if (ss != null) { studies.addAll(ss); numberOfStudies = studies.size(); }
    }
    
    public void removeStudies(Set<DicomStudy> ss) {
        if (ss != null) { studies.removeAll(ss); numberOfStudies = studies.size(); }
    }

    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPredicateString() { return predicateString; }
    public void setPredicateString(String predicateString) { this.predicateString = predicateString; }
    public Boolean isSmartAlbum() { return smartAlbum; }
    public void setSmartAlbum(Boolean smartAlbum) { this.smartAlbum = smartAlbum; }
    public Set<DicomStudy> getStudies() { return studies; }
    public void setStudies(Set<DicomStudy> studies) { this.studies = studies; this.numberOfStudies = studies != null ? studies.size() : 0; }
    public int getNumberOfStudies() { return numberOfStudies; }
    public void setNumberOfStudies(int numberOfStudies) { this.numberOfStudies = numberOfStudies; }

    /** Evaluate smart album predicate against a list of all studies. */
    public List<DicomStudy> evaluate(List<DicomStudy> allStudies) {
        if (!Boolean.TRUE.equals(smartAlbum) || predicateString == null || predicateString.trim().isEmpty())
            return new ArrayList<>(studies);
        return AlbumRuleEngine.filterStudies(allStudies, predicateString);
    }

    @Override public String toString() { return name != null ? name : "Album"; }
    
    @Override public boolean equals(Object obj) {
        if (this == obj) return true; if (!(obj instanceof DicomAlbum)) return false;
        DicomAlbum that = (DicomAlbum) obj;
        if (index != null && that.index != null) return index.equals(that.index);
        if (name != null && that.name != null) return name.equals(that.name); return super.equals(obj);
    }
    
    @Override public int hashCode() { if (index != null) return index.hashCode(); if (name != null) return name.hashCode(); return super.hashCode(); }
    
    @Override public int compareTo(DicomAlbum other) {
        if (other == null) return 1;
        if (index != null && other.index != null) return index.compareTo(other.index);
        if (index != null) return -1; if (other.index != null) return 1;
        if (name != null && other.name != null) return name.compareTo(other.name);
        if (name != null) return -1; if (other.name != null) return 1; return 0;
    }
}

