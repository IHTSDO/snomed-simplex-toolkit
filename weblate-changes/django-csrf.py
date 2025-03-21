"""
Blank version of Cross Site Request Forgery Middleware.
"""

from django.conf import settings
from django.urls import get_callable
from django.utils.deprecation import MiddlewareMixin

REASON_NO_REFERER = "Referer checking failed - no Referer."
REASON_NO_CSRF_COOKIE = "CSRF cookie not set."

def _get_failure_view():
    """Return the view to be used for CSRF rejections."""
    return get_callable(settings.CSRF_FAILURE_VIEW)

def rotate_token(request):
    """
    Change the CSRF token in use for a request - should be done on login
    for security purposes.
    """
    print('')


def get_token(request):
    print('')

class CsrfViewMiddleware(MiddlewareMixin):
    """
    This is an empty pass-through version of the original CSRF Middleware.
    """

    def _accept(self, request):
        # Avoid checking the request twice by adding a custom attribute to
        # request.  This will be relevant when both decorator and middleware
        # are used.
        request.csrf_processing_done = True
        return None

    def _reject(self, request, reason):
        return response

    def process_request(self, request):
        print('')


    def process_view(self, request, callback, callback_args, callback_kwargs):
        return self._accept(request)

    def process_response(self, request, response):
        request.META["CSRF_COOKIE_NEEDS_UPDATE"] = False
        return response
