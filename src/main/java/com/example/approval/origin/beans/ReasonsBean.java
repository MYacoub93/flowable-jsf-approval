package com.example.approval.origin.beans;

import java.io.Serializable;

/**
 * One row of the {@code sis_reasons} catalogue
 * ({@code CommonMapper.getTransactionReasons}): a withdrawal /
 * transaction reason with its bilingual descriptions.
 *
 * <p>{@code reasonCode} is the stored value; {@code reasonDesc} is the
 * English and {@code reasonDescS} the Arabic description ({@code _S} suffix
 * follows the existing SIS bilingual naming convention used across the
 * {@code origin.beans} package).</p>
 */
public class ReasonsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reasonCode;
    private String reasonDesc;
    private String reasonDescS;

    public ReasonsBean() {
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDesc() {
        return reasonDesc;
    }

    public void setReasonDesc(String reasonDesc) {
        this.reasonDesc = reasonDesc;
    }

    public String getReasonDescS() {
        return reasonDescS;
    }

    public void setReasonDescS(String reasonDescS) {
        this.reasonDescS = reasonDescS;
    }

    @Override
    public String toString() {
        return "ReasonsBean{reasonCode=" + reasonCode
                + ", reasonDesc=" + reasonDesc + "}";
    }
}