package org.zstack.header.candidate;

public class TypeBreakdown {
    private int total;
    private int selected;
    private int rejected;
    private int skipped;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public void count(String decision) {
        total++;
        if (CandidateTypes.SELECTED.equals(decision)) {
            selected++;
        } else if (CandidateTypes.REJECTED.equals(decision)) {
            rejected++;
        } else if (CandidateTypes.SKIPPED.equals(decision)) {
            skipped++;
        }
    }
}
