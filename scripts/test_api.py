"""Basic smoke test. Run after PostgreSQL, MQTT, backend and simulator are running.
Requires: pip install requests
"""
import uuid
import requests
BASE='http://localhost:8080/api'
email=f'test-{uuid.uuid4().hex[:8]}@example.com'
resp=requests.post(BASE+'/auth/register',json={'name':'Test User','email':email,'password':'Passw0rd123!'},timeout=5)
resp.raise_for_status()
resp=requests.post(BASE+'/auth/login',json={'email':email,'password':'Passw0rd123!'},timeout=5)
resp.raise_for_status()
h={'Authorization':'Bearer '+resp.json()['accessToken']}
scooters=requests.get(BASE+'/scooters',headers=h,timeout=5);scooters.raise_for_status()
print('Registered, logged in and listed',len(scooters.json()),'scooters')
print('NOTE: no ride created. For complete smoke test run README PowerShell demo.')
