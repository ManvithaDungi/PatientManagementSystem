// In-memory holder for the short-lived access token.
//
// The token is deliberately NOT persisted (no localStorage / sessionStorage) so
// that an XSS bug cannot read it back out of storage. After a page reload the
// token is re-issued from the HttpOnly, SameSite=Strict refresh cookie.
let accessToken = null;

export const getAccessToken = () => accessToken;

export const setAccessToken = (token) => {
   accessToken = token || null;
};

export const clearAccessToken = () => {
   accessToken = null;
};
