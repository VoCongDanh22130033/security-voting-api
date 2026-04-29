package com.nlu.electionservice.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class ElectionVoterId implements Serializable {
  private Long electionId;
  private Long voterId;
}