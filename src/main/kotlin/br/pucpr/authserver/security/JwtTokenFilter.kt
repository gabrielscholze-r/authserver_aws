package br.pucpr.authserver.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.GenericFilterBean

@Component
class JwtTokenFilter(private val jwt: Jwt) : GenericFilterBean() {
    private val log = LoggerFactory.getLogger(JwtTokenFilter::class.java)

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val path = request.requestURI
        val method = request.method

        if (
            (method.equals("POST", true) && path.endsWith("/users/login")) ||
            (method.equals("POST", true) && path.endsWith("/users")) ||
            path.startsWith("/h2-console") ||
            path.startsWith("/swagger") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/error")
        ) {
            chain.doFilter(req, res)
            return
        }

        val auth = jwt.extract(request)
        if (auth != null) {
            SecurityContextHolder.getContext().authentication = auth
        }

        chain.doFilter(req, res)
    }
}
