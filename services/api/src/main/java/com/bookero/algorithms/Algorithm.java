package com.bookero.algorithms;

public interface Algorithm {
  String key();

  String displayName();

  String family();

  String description();

  AlgorithmResult execute(AlgorithmContext ctx);
}
