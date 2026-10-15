export const testUser = { id: 'test-user', name: 'Test Reader', email: 'reader@example.test', role: 'USER', createdAt: '2026-10-12T08:00:00Z' }
export const testSession = { accessToken: 'test-token', tokenType: 'Bearer' as const, expiresInSeconds: 900, user: testUser }
export const emptyPage = { items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 }
export const testArticle = { id: 'test-article', headline: 'Test evidence headline', summary: 'Publisher supplied excerpt.', publishedAt: '2026-10-12T08:00:00Z', source: { id: 'test-source', name: 'Test Publisher', homepageUrl: 'https://publisher.example' }, url: 'https://publisher.example/article', imageUrl: 'https://publisher.example/photo.jpg', topics: [] }
