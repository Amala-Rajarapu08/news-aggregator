export const Loading = ({ text = 'Fetching latest news…' }) => <div className="card" role="status"><div className="skel" /><div className="skel short" /><p className="muted">{text}</p></div>
export const Empty = ({ text = 'No articles match your current interests.' }) => <div className="card center muted">{text}</div>
export const ErrorState = ({ text, retry }) => <div className="card center" role="alert"><p>{text || 'News service temporarily unavailable.'}</p>{retry && <button className="btn" onClick={retry}>Retry</button>}</div>
