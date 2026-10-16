import { LocalLinter, Dialect } from './package/dist/index.js';
import { binaryInlined } from './package/dist/binaryInlined.js';

let currentLinter = null;
let currentDialect = Dialect.American;
const userWords = new Set();
const ignoredHashes = new Set();

function resolveDialect(dialectName) {
  switch ((dialectName || '').toLowerCase()) {
    case 'british':
    case 'uk':
      return Dialect.British;
    case 'australian':
    case 'au':
      return Dialect.Australian;
    case 'canadian':
    case 'ca':
      return Dialect.Canadian;
    case 'indian':
    case 'in':
      return Dialect.Indian;
    case 'american':
    case 'us':
    default:
      return Dialect.American;
  }
}

function categorizeLintKind(kind) {
  const k = (kind || '').toLowerCase();
  if (k.includes('spell') || k.includes('typo') || k.includes('agreement') || k.includes('grammar') || k.includes('punctuation') || k.includes('capitalization')) {
    return { category: 'Correctness', severity: 'critical', color: '#EF4444' };
  }
  if (k.includes('readability') || k.includes('redundancy') || k.includes('boundary') || k.includes('wordorder')) {
    return { category: 'Clarity', severity: 'warning', color: '#F59E0B' };
  }
  if (k.includes('wordchoice') || k.includes('enhancement') || k.includes('style') || k.includes('malapropism')) {
    return { category: 'Engagement', severity: 'suggestion', color: '#3B82F6' };
  }
  return { category: 'Delivery', severity: 'suggestion', color: '#8B5CF6' };
}

function countSyllables(word) {
  word = word.toLowerCase().replace(/[^a-z]/g, '');
  if (word.length <= 3) return 1;
  word = word.replace(/(?:[^laeiouy]|ed|es|e)$/, '');
  word = word.replace(/^y/, '');
  const syllables = word.match(/[aeiouy]{1,2}/g);
  return syllables ? syllables.length : 1;
}

function calculateReadability(text) {
  const words = text.trim().split(/\s+/).filter(w => w.length > 0);
  const wordCount = words.length;
  if (wordCount === 0) {
    return {
      wordCount: 0,
      charCount: 0,
      sentenceCount: 0,
      readingTimeMinutes: 0,
      readabilityScore: 100,
      readabilityGrade: 'Easy'
    };
  }

  const charCount = text.length;
  const sentenceCount = Math.max(1, (text.match(/[.!?]+(?:\s+|$)/g) || []).length);
  
  let totalSyllables = 0;
  for (const w of words) {
    totalSyllables += countSyllables(w);
  }

  // Flesch Reading Ease Formula
  const score = Math.round(206.835 - 1.015 * (wordCount / sentenceCount) - 84.6 * (totalSyllables / wordCount));
  const clampedScore = Math.max(0, Math.min(100, score));

  let grade = 'Standard';
  if (clampedScore >= 80) grade = 'Very Easy';
  else if (clampedScore >= 60) grade = 'Standard';
  else if (clampedScore >= 40) grade = 'College Level';
  else grade = 'Advanced / Academic';

  const readingTimeMinutes = Math.max(1, Math.ceil(wordCount / 200));

  return {
    wordCount,
    charCount,
    sentenceCount,
    readingTimeMinutes,
    readabilityScore: clampedScore,
    readabilityGrade: grade
  };
}

async function initEngine(dialectName) {
  try {
    currentDialect = resolveDialect(dialectName);
    currentLinter = new LocalLinter({ binary: binaryInlined, dialect: currentDialect });
    await currentLinter.setup();

    if (userWords.size > 0) {
      await currentLinter.importWords(Array.from(userWords));
    }

    if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onEngineReady) {
      window.DasmoNativeBridge.onEngineReady(true);
    }
    console.log("Dasmo Grammar Engine Initialized with dialect:", dialectName);
    return true;
  } catch (err) {
    console.error("Failed to init Dasmo Grammar Engine:", err);
    if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onEngineError) {
      window.DasmoNativeBridge.onEngineError(String(err));
    }
    return false;
  }
}

async function setDialect(dialectName) {
  return initEngine(dialectName);
}

async function addWord(word) {
  if (!word || typeof word !== 'string') return;
  const trimmed = word.trim();
  if (trimmed) {
    userWords.add(trimmed);
    if (currentLinter && currentLinter.importWords) {
      try {
        await currentLinter.importWords([trimmed]);
      } catch (e) {
        console.warn("Failed to import word directly:", e);
      }
    }
  }
}

async function lintText(requestId, text) {
  if (!currentLinter) {
    await initEngine('American');
  }
  try {
    if (!text || text.trim().length === 0) {
      const emptyResult = JSON.stringify([]);
      if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onLintResult) {
        window.DasmoNativeBridge.onLintResult(requestId, emptyResult);
      }
      return emptyResult;
    }

    const lints = await currentLinter.lint(text);
    const results = [];
    for (const lint of lints) {
      const span = lint.span();
      const problem = text.substring(span.start, span.end);

      // Check if user has added this word to personal dictionary
      if (userWords.has(problem) || userWords.has(problem.toLowerCase())) {
        continue;
      }

      const kindStr = lint.lint_kind_pretty();
      const meta = categorizeLintKind(kindStr);
      const suggestions = lint.suggestions().map(s => s.get_replacement_text());

      results.push({
        id: results.length + 1,
        start: span.start,
        end: span.end,
        problem: problem,
        kind: kindStr,
        category: meta.category,
        severity: meta.severity,
        color: meta.color,
        message: lint.message(),
        messageHtml: lint.message_html ? lint.message_html() : lint.message(),
        suggestions: suggestions
      });
    }

    const resultJson = JSON.stringify(results);
    if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onLintResult) {
      window.DasmoNativeBridge.onLintResult(requestId, resultJson);
    }
    return resultJson;
  } catch (err) {
    console.error("Error linting text in Dasmo Grammar:", err);
    if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onLintError) {
      window.DasmoNativeBridge.onLintError(requestId, String(err));
    }
    return "[]";
  }
}

function getStats(requestId, text) {
  try {
    const stats = calculateReadability(text || '');
    const json = JSON.stringify(stats);
    if (typeof window !== 'undefined' && window.DasmoNativeBridge && window.DasmoNativeBridge.onStatsResult) {
      window.DasmoNativeBridge.onStatsResult(requestId, json);
    }
    return json;
  } catch (err) {
    console.error("Error calculating stats:", err);
    return "{}";
  }
}

if (typeof window !== 'undefined') {
  window.DasmoEngine = {
    init: initEngine,
    setDialect: setDialect,
    addWord: addWord,
    lint: lintText,
    getStats: getStats
  };
}

export { initEngine, setDialect, addWord, lintText, getStats };
