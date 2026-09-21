import {requestJson, requestVoid} from './http'

export interface WorkspaceDirectory {
    name: string;
    path: string
}

export interface WorkspaceFile {
    name: string;
    path: string;
    sizeBytes: number
}

export interface WorkspaceDirectoryView {
    current: string;
    parent: string | null;
    directories: WorkspaceDirectory[];
    files: WorkspaceFile[];
    canBrowseRoots: boolean
}

export function browseWorkspaces(path = '.'): Promise<WorkspaceDirectoryView> {
    return requestJson(`/api/workspaces?path=${encodeURIComponent(path)}`)
}

export function selectWorkspaceDirectory(): Promise<string | null> {
    return requestJson('/api/workspaces/select-directory', {method: 'POST'})
}

export type WorkspaceApplication = 'explorer' | 'cursor' | 'idea' | 'pycharm'

export function openWorkspaceInApplication(path: string, application: WorkspaceApplication): Promise<void> {
    return requestVoid('/api/workspaces/open', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({path, application}),
    })
}