{{- define "bsv.labels" -}}
app.kubernetes.io/part-of: {{ .Values.global.partOf | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
{{- end -}}

{{- define "bsv.selectorLabel" -}}
app.kubernetes.io/name: {{ . | quote }}
{{- end -}}
