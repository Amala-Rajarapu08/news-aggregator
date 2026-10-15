import { briefing } from '../data/briefing'
import { articles } from '../data/articles'
import { delay } from './config'
// Later: POST /api/ai/... -> FastAPI. Everything here is MOCK output.
export async function generateBriefing() { await delay(1200); return briefing }
export async function ask(question) {
  await delay(1000)
  const src = articles.slice(0, 3)
  return { question, answer: '[Demo answer] This is a canned mock response. A real grounded answer will come from the RAG pipeline.', sources: src, count: src.length }
}
export async function summarize() { await delay(800); return { points: ['[Demo] Summary point one.', '[Demo] Summary point two.', '[Demo] Summary point three.'], note: 'Mock summary, not AI-generated.' } }
