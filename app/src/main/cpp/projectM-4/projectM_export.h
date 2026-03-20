#pragma once

// For consuming the shared library, symbols are imported
#ifdef _WIN32
  #define PROJECTM_EXPORT __declspec(dllimport)
#else
  #define PROJECTM_EXPORT __attribute__((visibility("default")))
#endif
